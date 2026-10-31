'use strict';
const MODEL = 'gpt-4.1-mini';
class ApiError extends Error {constructor(status,message){super(message);this.status=status;}}
async function requestJSON(url,{body=null,key=null,timeout=30000}={}) {
 const controller = new AbortController(); const timer=setTimeout(()=>controller.abort(),timeout);
 try {
  const headers={'Accept':'application/json','User-Agent':'Veilradio-Desktop/0.5.19'};
  if(key)headers.Authorization='Bearer '+key;
  if(body!==null)headers['Content-Type']='application/json';
  let res;
  try{res=await fetch(url,{method:body===null?'GET':'POST',headers,body:body===null?undefined:JSON.stringify(body),signal:controller.signal});}
  catch(e){throw new ApiError(503,e.name==='AbortError'?'The request timed out. Please try again.':'Cannot reach the service. Check your internet connection.');}
  let obj;
  try{obj=await res.json()}catch(e){throw new ApiError(502,'The service returned an unreadable response.');}
  if(!res.ok){const msg=obj?.error?.message||obj?.error?.msg||'Service error '+res.status;
    if(res.status===401)throw new ApiError(401,'Invalid OpenAI API key.');
    if(res.status===429)throw new ApiError(429,'OpenAI rate limit or credits reached.');
    throw new ApiError(res.status===400?400:502,String(msg).slice(0,260));}
  return obj;
 } finally{clearTimeout(timer);}
}
async function ai(key,system,prompt,tokens=1900){
 const obj=await requestJSON('https://api.openai.com/v1/chat/completions',{
  key,timeout:60000,
  body:{model:MODEL,messages:[{role:'system',content:system},{role:'user',content:prompt}],max_tokens:tokens,
   response_format:{type:'json_object'},temperature:.85}
 });
 try{return JSON.parse(obj.choices[0].message.content);}catch(e){throw new ApiError(502,'The AI did not return valid JSON. Try again.');}
}
async function checkKey(key){const obj=await ai(key,'Return only JSON with {"ok":true}.','Connection check.',28);
 if(obj.ok!==true)throw new ApiError(401,'Connection check failed. Try again.');}
function norm(x){return String(x||'').normalize('NFKC').toLowerCase().replace(/[^a-z0-9]/g,'');}
function similar(a,b){a=norm(a);b=norm(b);if(!a||!b)return 0;if(a===b)return 1;const prev=Array.from({length:b.length+1},(_,i)=>i);let next=new Array(b.length+1);
 for(let i=1;i<=a.length;i++){next[0]=i;for(let j=1;j<=b.length;j++)next[j]=Math.min(next[j-1]+1,prev[j]+1,prev[j-1]+(a[i-1]===b[j-1]?0:1));for(let j=0;j<=b.length;j++)prev[j]=next[j];}return 1-prev[b.length]/Math.max(a.length,b.length);}
function parseTrack(d){if(!d?.id||!d?.title||!d?.artist?.name)return null;
 return {id:String(d.id),title:d.title,artist:d.artist.name,album:d.album?.title||'',preview:d.preview||'',rank:Number(d.rank)||0,url:'https://www.deezer.com/track/'+d.id,cover:d.album?.cover_medium||''};}
async function search(q,limit=10){q=String(q||'').trim().slice(0,160);if(!q)return [];
 const obj=await requestJSON('https://api.deezer.com/search?q='+encodeURIComponent(q)+'&limit='+Math.min(30,Math.max(1,limit||10)),{timeout:18000});
 if(obj.error)throw new ApiError(503,'Deezer catalogue is unavailable.');
 return (obj.data||[]).map(parseTrack).filter(Boolean);
}
async function track(id){id=String(id);if(!/^\d{1,16}$/.test(id))throw new ApiError(400,'Invalid track ID.');
 const obj=await requestJSON('https://api.deezer.com/track/'+id,{timeout:18000});
 if(obj.error)throw new ApiError(404,'Deezer track not found.');const t=parseTrack(obj);if(!t)throw new ApiError(404,'Track not found.');return t;
}
async function verify(idea){if(!idea||typeof idea!=='object')return null;
 const artist=String(idea.artist||'').trim().slice(0,100), title=String(idea.title||'').trim().slice(0,140);
 if(!artist||!title)return null;
 try{const hits=await search(artist+' '+title,14);
  const candidates=hits.map(t=>({t,a:similar(artist,t.artist),b:similar(title,t.title)}))
    .filter(x=>x.a>=.88&&x.b>=.86&&x.t.preview)
    .sort((x,y)=>(y.a+y.b)-(x.a+x.b));
  if(!candidates.length)return null;
  return {...candidates[0].t,reason:String(idea.reason||'').slice(0,180)};
 }catch(_){return null;}
}
async function discover(payload,key){
 const mode=String(payload.mode||'signal'),signal=String(payload.signal||'').trim().slice(0,500),seed=String(payload.seed||'').trim().slice(0,140);
 if(!['signal','seed','artists','drift'].includes(mode))throw new ApiError(400,'Unsupported discovery mode.');
 if(!signal&&!seed)throw new ApiError(400,'Add an artist, song, or sound description.');
 const depth=Math.max(0,Math.min(100,Number.isFinite(+payload.depth)?Math.round(+payload.depth):75));
 const round=Math.max(0,Math.min(9999,parseInt(payload.round,10)||0));
 const recent=payload.recent||{}, loved=Array.isArray(recent.love)?recent.love.slice(-12):[],passed=Array.isArray(recent.pass)?recent.pass.slice(-12):[];
 const ex=payload.exclude||{},excludedIds=new Set((ex.ids||[]).map(String)),excludedNames=new Set(ex.names||[]);
 const artists=mode==='artists'?[...new Set(signal.split(/[\n,;/|]+/).map(a=>a.trim()).filter(Boolean))].slice(0,5):[];
 const levels=[
  'FAMILIAR: recognizable yet tasteful artists and entry points. Prioritize fit over obvious global hits.',
  'LEFTFIELD: a mix of recognizable and lesser-known artists, underrated albums and independent labels.',
  'HIDDEN GEMS: underground adjacent artists, smaller independent releases, non-obvious collaborations and B-sides.',
  'DEEP UNDERGROUND: deliberately prioritize regional micro-scenes, underheard artists, rare but officially released tracks, niche labels, deep cuts. Avoid famous chart hits, algorithm staples and viral tracks.',
  'OBSCURE: very small but real released artists, local scenes, tiny labels, esoteric B-sides, underheard cross-border connections. Aggressively avoid mainstream performers and well-known catalogue songs.'
 ];
 const level=depth<20?0:depth<40?1:depth<65?2:depth<85?3:4;
 const system='You are an expert adventurous music curator with excellent knowledge of obscure global music scenes. Return JSON only with {"candidates":[{"artist":"...","title":"...","reason":"..."}]} containing 28 distinct REAL released recordings with precise titles. DO NOT fabricate artists or songs. All tracks must be searchable on Deezer and have playable previews. Favor human-sounding underground discoveries matching the requested vocals, rhythm, energy and texture, without substituting generic chart hits. Each candidate should be a different song; widen to adjacent scenes before repeating an artist. Deezer separately verifies every suggestion.';
 let prompt=mode==='artists'?`Create an evolving radio blending these favorite artists: ${artists.join(' / ')}. Explore related local scenes and less obvious collaborators, not just the artists\' own famous songs.`:`Discovery: ${mode}. Described sound: ${signal||'none'}. Track or idea seed: ${seed||'none'}.`;
 prompt+=` DISCOVERY DEPTH: ${depth}/100. ${levels[level]} This depth setting is a strong instruction, not a decorative label. At high depth, prefer genuinely obscure musicians instead of famous artists' popular tracks.`;
 prompt+=` ${String(payload.refillHint||'').slice(0,150)} Continuation batch #${round+1}. Bring a NEW set of less obvious neighbouring artists and releases rather than recycling the last batch. Loved: ${JSON.stringify(loved)}. Passed: ${JSON.stringify(passed)}. Previously classified or queued music should never repeat. Return 28 DIFFERENT real released songs in exact artist/title format.`;
 const suggestions=await ai(key,system,prompt,3500),ideas=Array.isArray(suggestions.candidates)?suggestions.candidates.slice(0,28):[];
 if(!ideas.length)throw new ApiError(502,'The curator returned no candidates.');
 const verified=[];const ids=new Set(),names=new Set(),artistCounts=new Map();
 // Verify at most 28 candidates, four concurrently, and build up to 12 new, playable recordings.
 for(let i=0;i<ideas.length&&verified.length<12;i+=4){const batch=await Promise.all(ideas.slice(i,i+4).map(verify));
  for(const t of batch){if(!t)continue;
   const name=norm(t.artist)+':'+norm(t.title);
   if(ids.has(t.id)||names.has(name)||excludedIds.has(t.id)||excludedNames.has(name))continue;
   const artistKey=norm(t.artist);if(depth>=65&&(artistCounts.get(artistKey)||0)>=2)continue;
   artistCounts.set(artistKey,(artistCounts.get(artistKey)||0)+1);
   ids.add(t.id);names.add(name);verified.push(t);if(verified.length===12)break;
  }}
 if(depth>=65){verified.sort((a,b)=>{
   const ar=a.rank>0?a.rank:250000,br=b.rank>0?b.rank:250000;
   return ar-br;
 });}
 if(!verified.length)throw new ApiError(422,'All suggestions were already heard or could not be verified on Deezer. Try changing depth or the signal.');
 return {tracks:verified,verified:verified.length,proposed:ideas.length,mode,depth,round,source:'DEEZER VERIFIED PREVIEWS'};
}

async function drift(payload,key){const node=String(payload.node||'melodic rap').trim().slice(0,120);
 const trail=Array.isArray(payload.trail)?payload.trail.slice(-6):[];
 const system='You curate an underground music connection graph. Return JSON only: {"kind":"ARTIST","note":"brief factual description","links":[{"name":"...","type":"...","why":"..."},...]}. Exactly six real related artist, song, genre, or music-idea nodes. No invented names.';
 const obj=await ai(key,system,`Explore ${node}; trail: ${JSON.stringify(trail)}. Return six unexpected but defensible linked nodes.`,650);
 const links=Array.isArray(obj.links)?obj.links.filter(x=>x?.name).slice(0,8).map(x=>({name:String(x.name).slice(0,100),type:String(x.type||'IDEA').slice(0,30),why:String(x.why||'').slice(0,130)})):[];
 if(!links.length)throw new ApiError(422,'No links returned. Try another starting point.');
 return {node,kind:String(obj.kind||'MUSIC').slice(0,40),note:String(obj.note||'').slice(0,250),links,source:'AI SUGGESTED / NOT CATALOGUE VERIFIED'};
}
module.exports={MODEL,ApiError,checkKey,search,track,discover,drift};