from pathlib import Path
from PIL import Image
from io import BytesIO
import base64,re

source=Path('app/src/main/assets/veilradio.html')
s=source.read_text(encoding='utf-8')
a=s.index('<aside class="desktop">')
b=s.index('</aside>',a)+len('</aside>')
sidebar='''<aside class="desktop desktopRoom">
<div class="deskHead"><div class="deskNum">VEILRADIO / DESKTOP EDITION 05.18</div><h2 class="deskTitle">listen in<br><em>soft focus.</em></h2><p class="deskDescription">Your own little listening room. Follow a feeling, discover a new voice, keep the music that stays.</p></div>
<div class="deskGuide"><span>01 / YOUR LISTENING ROOM</span><span>PRIVATE / PERSONAL</span></div>
<div class="deskQuick">
<button type="button" data-desktop-page="discover"><span class="quickNum">01</span><span><strong>discover something.</strong><small>ONE SONG, FIVE ARTISTS, ANY MOOD</small></span><span class="quickArrow">↗</span></button>
<button type="button" data-desktop-page="saved"><span class="quickNum">02</span><span><strong>the saved archive.</strong><small>LOVED / MAYBE / KNOWN</small></span><span class="quickArrow">↗</span></button>
<button type="button" data-desktop-page="player"><span class="quickNum">03</span><span><strong>local player.</strong><small>PLAY THE MUSIC ON YOUR DEVICE</small></span><span class="quickArrow">↗</span></button>
<button type="button" data-desktop-page="playlists"><span class="quickNum">04</span><span><strong>your collections.</strong><small>A PLACE FOR EVERY SONG</small></span><span class="quickArrow">↗</span></button>
</div>
<div class="deskBottom"><div class="deskGuide"><span>02 / YOUR SIGNAL</span><span>AI × DEEZER</span></div><div class="roundStatus" id="roundStatus">Discover new music with Deezer-verified previews. Add your API key under Settings.</div><div class="deskFooter"><span>veilradio.</span><button type="button" data-desktop-page="settings">PREFERENCES ↗</button></div></div>
</aside>'''
s=s[:a]+sidebar+s[b:]
style='''<style>
@media(min-width:900px){
html,body{height:100%;overflow:hidden}
body{background:linear-gradient(130deg,#f5f2f4,#ebe8ed 50%,#e9e2e8)}
.layout{box-sizing:border-box;display:grid;grid-template-columns:minmax(550px,760px) minmax(310px,370px);gap:clamp(30px,4.7vw,76px);align-items:center;justify-content:center;height:100vh;min-height:660px;padding:22px 42px 28px;max-width:1530px;margin:auto}
.app{height:calc(100vh - 50px);min-height:610px;max-height:980px;border-radius:27px;box-shadow:0 26px 65px #493d4e38,0 0 0 1px #26262b12}
.inside{padding:29px 32px 0}
.nav{margin-left:-32px;margin-right:-32px;height:74px;padding:0 24px 9px}
.navBtn{height:60px}.navBtn small{font-size:10px;letter-spacing:.045em}
.wordmark{font-size:34px}.subrow{padding-top:23px}
.info{max-width:540px}.track{font-size:clamp(55px,5.4vw,85px);line-height:.92}.artist{font-size:19px}
.pillZone{max-width:480px;margin-top:12px}
.pageSurface{inset:0 0 73px 0;padding:38px 40px 48px}
.vPlayerScreen .vPlayerTitle{font-size:clamp(46px,4.7vw,74px)}
.vPlayerMain{padding:0 45px 36px}.vPlayerTop{padding:35px 43px 0}
.vPlayerFrost{padding:20px 28px 18px;max-width:660px;width:100%;align-self:center}
.vSettingsFrame{width:100%;min-height:100%}
.desktopRoom{display:flex;max-width:370px;height:calc(100vh - 88px);max-height:900px;min-height:570px;justify-content:space-between;gap:18px;color:#3d3744}
.deskNum{font:10px/1.5 Typewriter,monospace;letter-spacing:.17em;color:#9b8c9a}
.deskTitle{font-size:clamp(53px,4.8vw,76px);line-height:.91;margin:22px 0 20px;letter-spacing:-.046em}
.deskTitle em{font-weight:400;color:#9c8a9c}
.deskDescription{font:13px/1.85 Typewriter,monospace;color:#847884;max-width:320px}
.deskGuide{display:flex;justify-content:space-between;gap:6px;padding:0 2px 12px;border-bottom:1px solid #c8bdc8;color:#8c7f8c;font:10px/1.35 Typewriter,monospace;letter-spacing:.05em}
.deskQuick{display:flex;flex-direction:column;border-bottom:1px solid #cec4ce}
.deskQuick button{border:0;border-bottom:1px solid #c9becb98;cursor:pointer;text-align:left;background:transparent;color:#433b48;display:grid;grid-template-columns:31px 1fr 20px;align-items:center;gap:8px;padding:15px 2px;transition:padding .2s,background .2s}
.deskQuick button:last-child{border-bottom:0}
.deskQuick button:hover,.deskQuick button.active{padding-left:9px;background:#fff5}
.deskQuick strong{display:block;font:normal 25px/.98 Optical,Georgia,serif;letter-spacing:-.012em}
.deskQuick small{display:block;margin-top:7px;color:#978c9a;font:9px/1.3 Typewriter,monospace;letter-spacing:.045em}
.quickNum{color:#a195a4;font:11px Typewriter,monospace}.quickArrow{font:20px Optical,Georgia,serif;color:#8c7b8d;text-align:right}
.deskBottom{margin-top:auto}.deskBottom .roundStatus{border:0;padding:14px 2px 10px;margin:0;color:#8f8290;font:11px/1.8 Typewriter,monospace;min-height:54px}
.deskFooter{display:flex;align-items:center;justify-content:space-between;border-top:1px solid #c5bac5;padding-top:17px}
.deskFooter span{font:27px Optical,Georgia,serif;letter-spacing:-.03em}
.deskFooter button{font:10px Typewriter,monospace;background:#ffffff70;color:#716673;border:1px solid #c5b9c6;padding:11px 13px;border-radius:99px;cursor:pointer}
}
@media(min-width:900px) and (max-height:765px){.desktopRoom{gap:7px;min-height:0}.deskTitle{font-size:55px;margin:12px 0}.deskQuick button{padding:9px 2px}.deskQuick strong{font-size:21px}.deskDescription{line-height:1.45}.deskBottom .roundStatus{padding:7px 2px;min-height:0}}
@media(min-width:900px) and (max-width:1150px){.layout{grid-template-columns:minmax(510px,1fr) 290px;gap:24px;padding:18px 25px}.desktopRoom{min-height:0}.deskQuick strong{font-size:22px}}
</style>'''
s=s.replace('</head>',style+'</head>',1)
script='''<script>
document.addEventListener('click',e=>{const b=e.target.closest('[data-desktop-page]');if(b)document.querySelector('.navBtn[data-page="'+b.dataset.desktopPage+'"]')?.click()});
const vApp=document.getElementById('app');if(vApp){const refresh=()=>document.querySelectorAll('[data-desktop-page]').forEach(b=>b.classList.toggle('active',b.dataset.desktopPage===vApp.dataset.page));new MutationObserver(refresh).observe(vApp,{attributes:true,attributeFilter:['data-page']});refresh();}
window.addEventListener('message',event=>{if(typeof event.data!=='string'||!event.data.includes('veilradio:reply'))return;try{let x=JSON.parse(event.data);if(x.type==='veilradio:reply'&&/^api[0-9]{1,10}$/.test(x.id))window.__veilBridgeDeliver?.(x.id,JSON.stringify(x.packet))}catch(e){}});
</script>'''
s=s.replace('</body>',script+'</body>',1)
m=re.search(r"const fixedSettingsDocument=new TextDecoder\(\)\.decode\(Uint8Array\.from\(atob\('([^']+)'\)",s)
if not m:raise RuntimeError('Settings embed not found.')
inner=base64.b64decode(m.group(1)).decode('utf-8')
inner=inner.replace('Your key stays on this Android device.','Your key stays in this Windows app.')
inner=inner.replace('Android connection unavailable. Restart the app.','Desktop connection unavailable. Restart Veilradio.')
inner=inner.replace('Invalid Android connection response.','Invalid desktop connection response.')
inner=inner.replace('CHECKING SERVER…','CHECKING APP…')
s=s[:m.start(1)]+base64.b64encode(inner.encode()).decode()+s[m.end(1):]
out=Path('desktop/assets/veilradio.html')
out.parent.mkdir(parents=True,exist_ok=True);out.write_text(s,encoding='utf-8')
src=Path('app/src/main/res/drawable-nodpi/veilradio_launcher.webp')
if not src.exists():raise RuntimeError('Chosen ivory app icon is missing.')
icon=Image.open(src).convert('RGBA').resize((512,512),Image.Resampling.LANCZOS)
target=Path('desktop/build/icon.ico');target.parent.mkdir(parents=True,exist_ok=True)
icon.save(target,format='ICO',sizes=[(256,256),(128,128),(64,64),(48,48),(32,32),(16,16)])
print('Prepared Windows desktop page',out.stat().st_size,'and ivory icon',target.stat().st_size)