from pathlib import Path

p=Path("app/src/main/assets/veilradio.html")
s=p.read_text(encoding="utf-8")
def once(old,new,label):
    global s
    if s.count(old)!=1:raise RuntimeError("Veilradio navigation change failed: "+label)
    s=s.replace(old,new)

legacy="document.querySelectorAll('.navBtn').forEach(b=>b.addEventListener('click',()=>{if(b.dataset.page==='discover')closeSheet();else sheetOpen(b.dataset.page)}));"
once(legacy,"/* Original prototype sheet navigation retired. Veilradio page controller owns the navbar. */","obsolete nav handler")

old="""function setPage(id){if(!['saved','discover','player','playlists','settings'].includes(id))return;if(id!=='discover'){savedRadioRequest++;savedRadioIntro=null;pause();}state.page=id;app.dataset.page=id;
 page.classList.toggle('active',id!=='discover');qa('.navBtn[data-page]',app).forEach(b=>{const active=b.dataset.page===id;b.classList.toggle('active',active);b.setAttribute('aria-current',active?'page':'false')});
 if(id==='discover'){page.innerHTML='';page.scrollTop=0;}else renderPage();saveState();}"""
new="""function setPage(id){
 if(!['saved','discover','player','playlists','settings'].includes(id))return;
 if(id!=='discover'){savedRadioRequest++;savedRadioIntro=null;pause();}
 // Clear stale playlist UI before switching, even if the new page throws.
 state.page=id;app.dataset.page=id;
 if(nodes.sheet)nodes.sheet.classList.remove('open');
 page.dataset.view=id;
 page.innerHTML='';page.scrollTop=0;
 page.classList.toggle('active',id!=='discover');
 qa('.navBtn[data-page]',app).forEach(b=>{const active=b.dataset.page===id;b.classList.toggle('active',active);b.setAttribute('aria-current',active?'page':'false')});
 if(id!=='discover'){
  try{renderPage()}catch(e){console.error('Veilradio screen navigation:',e);
   page.innerHTML='<div class="vFolderEmpty" style="padding:34px 22px">This page could not load. Tap its tab again to retry.</div>';
   alertUser('SCREEN ERROR · TAP TO RETRY');}
 }
 saveState();
}"""
once(old,new,"atomic screen switch")
once("if(b.dataset.playFile){const i=localFiles.findIndex(t=>t.id===b.dataset.playFile);playbackSetId=state.page==='playlists'&&state.localPlaylist?state.localPlaylist:null;resetShuffleCycle();await loadLocal(i,true);setPage('player');return}",
     "if(b.dataset.playFile){const i=localFiles.findIndex(t=>t.id===b.dataset.playFile);playbackSetId=state.page==='playlists'&&state.localPlaylist?state.localPlaylist:null;resetShuffleCycle();setPage('player');await loadLocal(i,true);return}",
     "playlist song navigation")
once("setSkin();applyPrefs();applyNavPrefs();setPage('discover')}",
     "setSkin();applyPrefs();applyNavPrefs();if(!navigationWasTouched)setPage('discover');else if(state.page!=='discover')renderPage()}",
     "avoid late initialization navigation")
once("const navStyles=[['capsule'", "let navigationWasTouched=false;\nconst navStyles=[['capsule'", "nav state flag")
once("qa('.navBtn[data-page]',app).forEach(b=>b.addEventListener('click',e=>{e.stopImmediatePropagation();e.preventDefault();setPage(b.dataset.page)},true));",
     "qa('.navBtn[data-page]',app).forEach(b=>b.addEventListener('click',e=>{e.stopImmediatePropagation();e.preventDefault();navigationWasTouched=true;setPage(b.dataset.page)},true));","capture nav ownership")
p.write_text(s,encoding="utf-8")
print("Veilradio 05.16: navigation patched and verified")
