/* VEILRADIO 05.19 / UNDERGROUND DEPTH + ENDLESS DISCOVERY
   Keeps the original store, reactions, playlists and session IDs intact. */
state.discoveryDepth=Number.isFinite(Number(state.discoveryDepth))?
 Math.max(0,Math.min(100,Math.round(Number(state.discoveryDepth)))):78;
let radioEpoch=0,radioFetching=false,radioFailures=0,radioRequestCounter=0;
function radioDepthLabel(v){return v<20?'FAMILIAR':v<40?'LEFTFIELD':v<65?'HIDDEN GEMS':v<85?'UNDERGROUND':'OBSCURE';}
function updateDepthLabels(){
 const v=state.discoveryDepth;
 const show=qs('#vRadioDepthValue');if(show)show.textContent=String(v).padStart(2,'0')+' / 100';
 const note=qs('#vRadioDepthMood');if(note)note.textContent=radioDepthLabel(v);
 const bar=qs('#vRadioDepthRange');if(bar&&Number(bar.value)!==v)bar.value=String(v);
 const quick=qs('#vDepthQuick');if(quick)quick.textContent='DEPTH / '+radioDepthLabel(v);
}
const priorDiscoveryGateway=openDiscoveryGateway;
openDiscoveryGateway=function(){
 priorDiscoveryGateway();
 const root=qs('#veilFocusResults');
 root.insertAdjacentHTML('afterbegin',`<div class="vRadioDepthPanel">
 <div class="vRadioDepthHeading"><span>00 / DISCOVERY DEPTH</span><strong id="vRadioDepthValue">${String(state.discoveryDepth).padStart(2,'0')} / 100</strong></div>
 <input id="vRadioDepthRange" aria-label="Discovery depth, familiar to obscure" type="range" min="0" max="100" step="5" value="${state.discoveryDepth}">
 <div class="vRadioDepthLabels"><span>FAMILIAR</span><span id="vRadioDepthMood">${radioDepthLabel(state.discoveryDepth)}</span><span>OBSCURE</span></div>
 <p>More depth means smaller scenes, deep cuts and fewer obvious recommendations. Adjust it even while a radio is playing.</p>
 <div class="vRadioEndless"><span>✧ ENDLESS DISCOVERY</span><span>ON / AUTO-REFILL</span></div></div>`);
 updateDepthLabels();
};
focus.addEventListener('input',e=>{
 if(e.target.id!=='vRadioDepthRange')return;
 state.discoveryDepth=Math.max(0,Math.min(100,parseInt(e.target.value,10)||0));
 if(state.radioSession)state.radioSession.depth=state.discoveryDepth;
 saveState();updateDepthLabels();
});
function addDepthQuickControl(){
 const anchor=qs('#openSignal');if(!anchor||qs('#vDepthQuick'))return;
 const btn=document.createElement('button');btn.type='button';btn.id='vDepthQuick';btn.className='vDepthQuick';
 btn.textContent='DEPTH / '+radioDepthLabel(state.discoveryDepth);
 btn.title='Change the radio discovery depth';
 btn.addEventListener('click',e=>{e.preventDefault();e.stopPropagation();openDiscoveryGateway()});
 anchor.insertAdjacentElement('afterend',btn);
}
addDepthQuickControl();
function radioExclusions(){
 const categorized=discoveryExclusions(),ids=new Set(categorized.ids),names=new Set(categorized.names);
 // Include uncategorized songs already queued, avoiding repeats before user rates them.
 const first=Math.max(0,liveTest.queueFrom);
 for(const t of tracks.slice(first)){
  for(const k of identityKeys(t)){
   if(k.startsWith('deezer:'))ids.add(k.slice(7));
   else if(k.startsWith('recording:'))names.add(k.slice(10));
  }
 }
 return {ids:[...ids].slice(-450),names:[...names].slice(-450)};
}
function radioRemaining(){
 if(liveTest.queueFrom<0)return 0;
 let count=0;const lo=Math.max(liveTest.queueFrom,index+1);
 for(let i=lo;i<=liveTest.queueTo;i++)if(!alreadyCategorized(tracks[i]))count++;
 return count;
}
function setRadioMessage(message,secondary=''){
 if(state.page==='discover' && nodes.status)
  nodes.status.innerHTML=message+(secondary?'<br><strong>'+secondary+'</strong>':'');
}
async function fetchRadioBatch(initial,epoch,extraHint=''){
 if(radioFetching||!state.radioSession||epoch!==radioEpoch)return;
 const session=state.radioSession;
 const requestNo=++radioRequestCounter;
 radioFetching=true;
 const results=qs('#veilFocusResults'),submit=qs('#veilFocusSubmit');
 if(initial){if(results)results.innerHTML='<p class="vEmpty">LOOKING BEYOND THE OBVIOUS…<br>CHECKING REAL DEEZER PREVIEWS.</p>';if(submit)submit.disabled=true;}
 else if(index<0)setRadioMessage('following the signal…','FINDING MORE VERIFIED TRACKS');
 try{
  const exclusions=radioExclusions();
  const batch=await livePost('/api/discover',{mode:session.mode,signal:session.signal,
   seed:session.seed,depth:state.discoveryDepth,round:session.round||0,refillHint:extraHint,recent:liveRecent(),exclude:exclusions});
  if(epoch!==radioEpoch)return;
  const seen=new Set();
  const originalKeys=new Set(tracks.flatMap(t=>identityKeys(t)));
  const fresh=(Array.isArray(batch.tracks)?batch.tracks:[]).filter(t=>{
   if(!t||alreadyCategorized(t))return false;
   const keys=identityKeys(t);if(!keys.length||keys.some(k=>seen.has(k)||originalKeys.has(k)))return false;
   keys.forEach(k=>seen.add(k));return true;
  });
  if(!fresh.length)throw new Error('The curator could not find new playable recordings. Try a different depth or seed.');
  const start=tracks.length,stamp=Date.now().toString(36)+'_'+requestNo;
  const appended=fresh.map((t,i)=>({...t,num:'live'+stamp+'_'+i,
   genre:'DEEZER / '+session.mode.toUpperCase(),art:i%images.length,deezerId:t.id,verifiedAt:Date.now()}));
  if(initial){liveTest.queueFrom=start;}
  tracks.push(...appended);liveTest.queueTo=tracks.length-1;
  state.sessionQueue=[...(state.sessionQueue||[]),...appended].slice(-160);
  for(const t of appended){state.linkMap[t.num]=t.url;if(t.preview)state.previewMap[t.num]=t.preview;}
  session.round=(session.round||0)+1;
  session.depth=state.discoveryDepth;
  state.radioSession={...session};
  if(session.signal)state.signal=session.signal;
  if(session.seed)state.seed=session.seed;
  radioFailures=0;
  saveState();
  const needPlay=initial||index<0||!current()||alreadyCategorized(current());
  if(initial){
   closeFocus();setPage('discover');index=start;loadTrack();setPlaying(true);
   alertUser(appended.length+' TRACKS · '+radioDepthLabel(state.discoveryDepth));
  }else if(needPlay){
   index=start;loadTrack();if(state.page==='discover')setPlaying(true);
   alertUser('RADIO CONTINUES · '+appended.length+' MORE SONGS');
  }
 }catch(err){
  if(epoch!==radioEpoch)return;
  if(initial)liveError(err.message);
  else{
   radioFailures++;
   if(index<0){renderQueueComplete();setRadioMessage('the signal paused.','RETRY OR ADJUST YOUR DEPTH');}
   alertUser('DISCOVERY REFILL · '+String(err.message).slice(0,75));
   if(radioFailures===1 && index<0){
    state.radioSession.round=(state.radioSession.round||0)+1;saveState();
    setTimeout(()=>{if(epoch===radioEpoch&&!radioFetching&&radioFailures===1&&state.page==='discover')
      void fetchRadioBatch(false,epoch,'Explore fresh adjacent micro-scenes and less obvious artist connections.');},1800);
   }
  }
 }finally{
  if(epoch===radioEpoch){radioFetching=false;if(submit)submit.disabled=false;}
 }
}
// New radios replace the active queue only. Older classified songs stay in Saved.
liveGenerate=async function(mode,signal='',seed=''){
 if(!['signal','artists','seed','drift'].includes(mode))return;
 const epoch=++radioEpoch;
 radioFetching=false;radioFailures=0;
 state.radioSession={mode,signal,seed,depth:state.discoveryDepth,round:0};
 saveState();
 return fetchRadioBatch(true,epoch);
};
function maybeRefillRadio(){
 if(!state.radioSession||radioFetching||radioFailures>=2)return;
 if(state.page!=='discover')return;
 if(liveTest.queueFrom<0)return;
 if(radioRemaining()>3 && index>=0)return;
 void fetchRadioBatch(false,radioEpoch);
}
document.addEventListener('click',e=>{if(!e.target.closest('[data-radio-retry]'))return;
 radioFailures=0;if(state.radioSession)void fetchRadioBatch(false,radioEpoch);});
const priorRadioLoadTrack=loadTrack;
loadTrack=function(){
 priorRadioLoadTrack();
 if(state.radioSession && state.page==='discover')queueMicrotask(maybeRefillRadio);
};
const priorRadioReact=react;
react=function(kind){
 const last=current(),wasAllowed=last&&!alreadyCategorized(last)&&state.page==='discover';
 priorRadioReact(kind);
 if(wasAllowed)queueMicrotask(maybeRefillRadio);
};
const priorQueueComplete=renderQueueComplete;
renderQueueComplete=function(){
 priorQueueComplete();
 if(state.radioSession){
  nodes.track.textContent=radioFetching?'finding more.':'more to discover.';
  nodes.artist.textContent=radioFetching?'beyond this batch.':'your radio continues.';
  nodes.genre.textContent='RADIO / '+radioDepthLabel(state.discoveryDepth);
  setRadioMessage(radioFetching?'following the signal…':'still discovering.',
   radioFetching?'CURATING MORE VERIFIED SONGS':radioFailures>=2?'TAP RETRY BELOW OR CHANGE DEPTH':'TAP / SIGNAL TO ADJUST YOUR DEPTH');
  if(radioFailures>=2 && nodes.status){
   nodes.status.insertAdjacentHTML('beforeend','<div style="margin-top:12px"><button type="button" data-radio-retry class="sceneBtn">↻ RETRY RADIO</button></div>');
  }
 }
};
// Preserve the live radio when reopening Veilradio. Only attempt an automatic refill
// if an existing radio session has run out; no new key prompt and no fabricated tracks.
if(state.radioSession && ['signal','artists','seed','drift'].includes(state.radioSession.mode)){
 state.radioSession.depth=state.discoveryDepth;
 radioEpoch++;
}else state.radioSession=null;
Promise.resolve().then(()=>{updateDepthLabels();if(state.radioSession&&state.page==='discover')maybeRefillRadio();});
