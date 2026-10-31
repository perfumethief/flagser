from pathlib import Path

def apply(html: str) -> str:
    anchor='initialise().then(restoreUncategorizedOnStart);\n})();</script>'
    if html.count(anchor)!=1:
        raise RuntimeError('Existing Veilradio discovery script changed. Refusing unsafe patch.')
    js=Path(__file__).with_name('discovery_addon_0519.js').read_text(encoding='utf-8')
    html=html.replace(anchor,'initialise().then(restoreUncategorizedOnStart);\n'+js+'\n})();</script>')
    css='''
/* 05.19: discovery-depth studio */
.vOverlay[data-mode="gateway"] .vResults{max-height:min(59dvh,500px)}
.vRadioDepthPanel{padding:15px 18px 12px;border:1px solid #8a788a75;border-radius:15px;background:linear-gradient(145deg,#ffffffb9,#e9e0e7dd);margin:0 0 9px;color:#544654;}
.vRadioDepthHeading{display:flex;justify-content:space-between;align-items:center;gap:10px;font:11px Typewriter,monospace;letter-spacing:.05em;}
.vRadioDepthHeading strong{font-size:14px;font-weight:400;color:#45394c;}
.vRadioDepthPanel input[type=range]{display:block;width:100%;margin:14px 0 12px;accent-color:#79647a;cursor:pointer;}
.vRadioDepthLabels{display:flex;justify-content:space-between;gap:5px;font:10px Typewriter,monospace;color:#786a79;letter-spacing:.035em;}
#vRadioDepthMood{color:#4c3e52;}
.vRadioDepthPanel p{font:11px/1.55 Typewriter,monospace;color:#887b87;margin:11px 0 9px;letter-spacing:0;}
.vRadioEndless{display:flex;justify-content:space-between;gap:6px;border-top:1px solid #baabba90;padding-top:11px;font:10px Typewriter,monospace;}
.vRadioEndless span:last-child{color:#785f7b;}
.vDepthQuick{background:#faf5f838;border:1px solid #fdf8fc80;border-radius:99px;color:#fff;padding:7px 12px;margin-left:9px;letter-spacing:.055em;font:10px/1.25 Typewriter,monospace;white-space:nowrap;cursor:pointer;}
.vDepthQuick:hover{background:#fdf9fc59;}
@media(max-width:600px){.vDepthQuick{font-size:9px;padding:6px 8px;margin-left:5px;}.vOverlay[data-mode="gateway"] .vResults{max-height:min(59dvh,500px)}
.vRadioDepthPanel{padding:12px 14px;}}
'''
    marker='</style><script>(function(){'
    if html.count(marker)!=1:raise RuntimeError('CSS insertion anchor changed')
    html=html.replace(marker,css+marker,1)
    return html

if __name__=='__main__':
    import sys
    path=Path(sys.argv[1]) if len(sys.argv)>1 else Path('desktop/assets/veilradio.html')
    path.write_text(apply(path.read_text(encoding='utf-8')),encoding='utf-8')
    print('Veilradio 05.19 endless discovery and niche slider installed.')