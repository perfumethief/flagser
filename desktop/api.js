'use strict';
const MODEL='gpt-4.1-mini';
class ApiError extends Error{constructor(status,message){super(message);this.status=status;}}
async function getJSON(url,{body=null,key=null,timeout=60000}={}){
 const c=new AbortController(),timer=setTimeout(()=>c.abort(),timeout);
 try{
  const headers={Accept:'application/json'};
  if(key)headers.Authorization='Bearer '+key;
  if(body!==null)headers['Content-Type']='application/json';
  let r;try{r=await fetch(url,{method:body===null?'GET':'POST',headers,body:body===null?undefined:JSON.stringify(body),signal:c.signal});}
  catch(e){throw new ApiError(503,e.name==='AbortError'?'The service timed out.':'Cannot connect. Check your internet connection.');}
  let d;try{d=await r.json();}catch(e){throw new ApiError(502,'The service did not return JSON.');}
  if(!r.ok){const msg=d?.error?.message||'HTTP '+r.status;
    if(r.status===401)throw new ApiError(401,'Invalid OpenAI API key.');
    if(r.status===429)throw new ApiError(429,'OpenAI rate limit or credits reached.');
    throw new ApiError(r.status===400?400:502,msg);}
  return d;
 }finally{clearTimeout(timer);}
}
async function ai(key,system,prompt,tokens=2000){
 const r=await getJSON('https://api.openai.com/v1/chat/completions',{key,body:{
  model:MODEL,messages:[{role:'system',content:system},{role:'user',content:prompt}],
  max_tokens:tokens,temperature:.85,response_format:{type:'json_object'}}});
 try{return JSON.parse(r.choices[0].message.content);}catch(e){throw new ApiError(502,'AI response was not valid JSON.');}
}
async function checkKey(key){const r=await ai(key,'Return JSON {"ok":true}.','Connection check.',28);
 if(r.ok!==true)throw new ApiError(401,'Connection check failed.');}
const norm=x=>String(x||'').toLowerCase().replace(/[^a-z0-9]/g,'');
function similar(a,b){
 a=norm(a);b=norm(b);if(a===b)return 1;
 const p=Array.from({length:b.length+1},(_,i)=>i),q=new Array(p.length);
 for(let i=1;i<=a.length;i++){q[0]=i;for(let j=1;j<=b.length;j++)q[j]=Math.min(q[j-1]+1,p[j]+1,p[j-1]+(a[i-1]===b[j-1]?0:1));for(let j=0;j<p.length;j++)p[j]=q[j];}
 return 1-p[b.length]/Math.max(1,a.length,b.length);
}
function parseTrack(x){if(!x?.id||!x?.title||!x?.artist?.name)return null;return{
 id:String(x.id),artist:x.artist.name,title:x.title,album:x.album?.title||'',preview:x.preview||'',
 url:'https://www.deezer.com/track/'+x.id,cover:x.album?.cover_medium||''};}
async function search(q,limit=10){q=String(q||'').trim().slice(0,160);if(!q)return [];
 const r=await getJSON('https://api.deezer.com/search?q='+encodeURIComponent(q)+'&limit='+Math.max(1,Math.min(30,limit||10)),{timeout:18000});
 if(r.error)throw new ApiError(503,'Deezer catalogue unavailable.');
 return (r.data||[]).map(parseTrack).filter(Boolean);
}
async function track(id){id=String(id||'');if(!/^\d{1,16}$/.test(id))throw new ApiError(400,'Invalid Deezer ID.');
 const r=await getJSON('https://api.deezer.com/track/'+id,{timeout:18000});
 if(r.error)throw new ApiError(404,'Track unavailable.');
 const t=parseTrack(r);if(!t)throw new ApiError(404,'Track unavailable.');return t;
}
async function verify(idea){
 if(!idea?.artist||!idea?.title)return null;
 try{
 const items=await search(String(idea.artist)+' '+String(idea.title),14);
 const matches=items.map(t=>({t,a:similar(idea.artist,t.artist),b:similar(idea.title,t.title)}))
  .filter(x=>x.a>=.88&&x.b>=.86&&x.t.preview).sort((x,y)=>(y.a+y.b)-(x.a+x.b));
 return matches.length?{...matches[0].t,reason:String(idea.reason||'').slice(0,180)}:null;
 }catch(e){return null;}
}
async function discover(p,key){
 const mode=String(p.mode||'signal'),signal=String(p.signal||'').trim().slice(0,500),seed=String(p.seed||'').trim().slice(0,140);
 if(!['signal','seed','artists','drift'].includes(mode))throw new ApiError(400,'Unsupported discovery mode.');
 if(!signal&&!seed)throw new ApiError(400,'Describe a sound or enter an artist first.');
 const loved=Array.isArray(p.recent?.love)?p.recent.love.slice(-12):[];
 const passed=Array.isArray(p.recent?.pass)?p.recent.pass.slice(-12):[];
 const exclusions=p.exclude||{},ids=new Set((exclusions.ids||[]).map(String)),names=new Set(exclusions.names||[]);
 const artists=mode==='artists'?[...new Set(signal.split(/[\n,;/|]+/).map(x=>x.trim()).filter(Boolean))].slice(0,5):[];
 const system='You curate interesting underground-leaning real released recordings. Return JSON with only a candidates array of 18 objects, each with artist,title,reason. No invented titles or duplicates. Deezer verifies the recordings separately. Prioritize distinct melodic voices and fitting rhythms.';
 const prompt=(mode==='artists'?'Blended radio of these favourite artists: '+artists.join(' / ')+'. Include nearby artists, influences and collaborations.':'Mode: '+mode+'. Signal: '+signal+'. Starting song/concept: '+seed)+
  '. Loved: '+JSON.stringify(loved)+'. Passed: '+JSON.stringify(passed)+'. Return 18 NEW exact real artist/song pairings; avoid categorized songs.';
 const obj=await ai(key,system,prompt),ideas=Array.isArray(obj.candidates)?obj.candidates.slice(0,18):[];
 if(!ideas.length)throw new ApiError(502,'The AI curator returned no candidates.');
 let result=[],seen=new Set();
 for(let i=0;i<ideas.length&&result.length<9;i+=4){
  const batch=await Promise.all(ideas.slice(i,i+4).map(verify));
  for(const t of batch){if(!t)continue;const name=norm(t.artist)+':'+norm(t.title);
    if(seen.has(t.id)||ids.has(t.id)||names.has(name))continue;
    result.push(t);seen.add(t.id);if(result.length===9)break;}
 }
 if(!result.length)throw new ApiError(422,'No verified Deezer previews. Try another seed.');
 return {tracks:result,verified:result.length,proposed:ideas.length,mode,source:'DEEZER VERIFIED PREVIEWS'};
}
async function drift(p,key){
 const node=String(p.node||'melodic rap').trim().slice(0,120);
 const system='Return JSON with kind,note and six links array. Each link has name,type,why. Every link must be a real artist, genre, track or concept. Return only JSON.';
 const obj=await ai(key,system,'Find six interesting real music connections for '+node+'. Previous trail: '+JSON.stringify(p.trail||[]),650);
 const links=Array.isArray(obj.links)?obj.links.filter(x=>x?.name).slice(0,8).map(x=>({
  name:String(x.name).slice(0,100),type:String(x.type||'IDEA').slice(0,30),why:String(x.why||'').slice(0,130)})):[];
 if(!links.length)throw new ApiError(422,'No links returned.');
 return {node,kind:String(obj.kind||'MUSIC'),note:String(obj.note||''),links,source:'AI SUGGESTED / NOT CATALOGUE VERIFIED'};
}
module.exports={MODEL,ApiError,checkKey,search,track,discover,drift};