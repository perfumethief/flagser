from pathlib import Path
import sys

p=Path(sys.argv[1]) if len(sys.argv)>1 else Path('app/src/main/assets/veilradio.html')
s=p.read_text(encoding='utf8')
marker='</style><script>(function(){'
css='''
/* 05.21: constrain the Signal/gateway overlays to the actual phone viewport */
@media(max-width:640px){
 .app .vOverlay.open{
  position:fixed!important;inset:0!important;width:100vw!important;
  height:100dvh!important;min-height:0!important;max-height:100dvh!important;
  overflow-y:auto!important;overscroll-behavior:contain!important;scrollbar-width:none!important;
  z-index:214748100!important;box-sizing:border-box!important
 }
 .app .vOverlay.open .vFocus{margin:auto 0!important;width:100%!important;max-height:none!important;min-height:0!important}
 .app .vOverlay.open .vResults{flex-shrink:0!important}
}
'''
if s.count(marker)!=1:raise RuntimeError('Phone overlay CSS insertion marker changed')
p.write_text(s.replace(marker,css+marker,1),encoding='utf8')
print('Fixed phone overlay viewport scrolling')
