from pathlib import Path
import base64

path = Path("app/src/main/assets/veilradio.html")
html = path.read_text(encoding="utf-8")
anchor = "const fixedSettingsDocument=new TextDecoder().decode(Uint8Array.from(atob('"
start = html.index(anchor) + len(anchor)
end = html.index("'", start)
settings = base64.b64decode(html[start:end]).decode("utf-8")
old = "const u=new URL(raw,location.href);if(u.pathname.startsWith('/api/'))return window.top.__veilBridgeRequest(raw,options||{})"
new = "if(raw.startsWith('/api/'))return window.top.__veilBridgeRequest(raw,options||{})"
if settings.count(old) != 1:
    raise RuntimeError("Expected the exact embedded API fetch shim once; build stopped rather than shipping the old bug.")
settings = settings.replace(old, new)
html = html[:start] + base64.b64encode(settings.encode("utf-8")).decode("ascii") + html[end:]
path.write_text(html, encoding="utf-8")
print("Patched Settings API connection to use the native Android bridge directly.")
