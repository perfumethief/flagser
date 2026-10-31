from pathlib import Path
import base64

path = Path("app/src/main/assets/veilradio.html")
html = path.read_text(encoding="utf-8")
anchor = "const fixedSettingsDocument=new TextDecoder().decode(Uint8Array.from(atob('"
start = html.index(anchor) + len(anchor)
end = html.index("'", start)
settings = base64.b64decode(html[start:end]).decode("utf-8")
old_fetch = "const u=new URL(raw,location.href);if(u.pathname.startsWith('/api/'))return window.top.__veilBridgeRequest(raw,options||{})"
new_fetch = "if(raw.startsWith('/api/'))return window.top.__veilBridgeRequest(raw,options||{})"
if old_fetch in settings:
    settings = settings.replace(old_fetch,new_fetch)
old_request = "const res=await fetch(path,{...(params||{}),cache:'no-store'});"
new_request = "const res=await window.top.__veilBridgeRequest(path,{...(params||{}),cache:'no-store'});"
if old_request in settings:
    settings = settings.replace(old_request,new_request)
if new_request not in settings:
    raise RuntimeError("Unsupported Settings source: native API bridge still missing.")
html = html[:start] + base64.b64encode(settings.encode("utf-8")).decode("ascii") + html[end:]
path.write_text(html, encoding="utf-8")
print("Settings API now calls the native Android bridge directly.")
