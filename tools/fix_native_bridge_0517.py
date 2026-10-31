from pathlib import Path
import base64,re

path=Path('app/src/main/assets/veilradio.html')
text=path.read_text(encoding='utf-8')
pattern=r'<script>\\(function\\(\\)\\{const orig=window\\.fetch\\.bind\\(window\\),pending=new Map\\(\\);.*?</script>'
# Use literal delimiting so the patch stops if the expected native bridge isn't present.
start=text.find('<script>(function(){const orig=window.fetch.bind(window),pending=new Map();')
end=text.find('</script>',start)
if start<0 or end<0:raise RuntimeError('Android bridge signature changed; refusing broken APK.')
end+=len('</script>')
new="""<script>(function(){
 const originalFetch=window.fetch.bind(window),pending=new Map();let nextId=0;
 window.__veilBridgeDeliver=function(id,raw){
  const entry=pending.get(id);if(!entry)return;
  pending.delete(id);clearTimeout(entry.timer);
  try{const packet=JSON.parse(raw),status=Number(packet.status)||200;
   entry.resolve(new Response(JSON.stringify(packet.body||{}),{status,headers:{'Content-Type':'application/json'}}));
  }catch(error){entry.reject(error)}
 };
 window.__veilBridgeRequest=function(path,options={}){
  if(!window.VeilNative||typeof window.VeilNative.request!=='function')
   return Promise.reject(new Error('Android connection unavailable. Restart Veilradio.'));
  let u;try{u=new URL(path,'https://appassets.androidplatform.net/')}catch(error){return Promise.reject(error)}
  if(u.origin!=='https://appassets.androidplatform.net'||!u.pathname.startsWith('/api/'))
   return Promise.reject(new Error('Invalid API path'));
  let payload={};try{payload=options.body?JSON.parse(options.body):{}}
  catch(error){return Promise.reject(new Error('Invalid API request'))}
  return new Promise((resolve,reject)=>{
   const id='api'+(++nextId);
   const timer=setTimeout(()=>{pending.delete(id);reject(new Error('Connection timed out. Check internet and try again.'))},95000);
   pending.set(id,{resolve,reject,timer});
   try{window.VeilNative.request(id,u.pathname+u.search,JSON.stringify(payload))}
   catch(error){clearTimeout(timer);pending.delete(id);reject(error)}
  });
 };
 window.fetch=function(input,options){
  const raw=typeof input==='string'?input:(input&&input.url)||'';
  let u;try{u=new URL(raw,'https://appassets.androidplatform.net/')}catch(error){return originalFetch(input,options)}
  if(u.origin==='https://appassets.androidplatform.net'&&u.pathname.startsWith('/api/'))
   return window.__veilBridgeRequest(u.pathname+u.search,options||{});
  return originalFetch(input,options);
 };
})();</script>"""
text=text[:start]+new+text[end:]
needle="const fixedSettingsDocument=new TextDecoder().decode(Uint8Array.from(atob('"
start=text.index(needle)+len(needle)
end=text.index("'",start)
settings=base64.b64decode(text[start:end]).decode('utf-8')
old="""async function apiRequest(path,params){
 const res=await window.top.__veilBridgeRequest(path,{...(params||{}),cache:'no-store'});
 let out;try{out=await res.json()}catch(e){throw Error('The local server is not running.')}
 if(!res.ok)throw Error(out.error||'Request failed');return out;
}"""
new_req="""async function apiRequest(path,params){
 const options={...(params||{}),cache:'no-store'};
 if(path==='/api/connect'){
  let payload={};try{payload=JSON.parse(options.body||'{}')}catch(e){}
  payload.remember=!!document.getElementById('apiRememberKey')?.checked;
  options.body=JSON.stringify(payload);
 }
 const bridge=window.parent?.__veilBridgeRequest;
 if(typeof bridge!=='function')throw Error('Android connection unavailable. Restart the app.');
 const res=await bridge(path,options);
 let out;try{out=await res.json()}catch(e){throw Error('Invalid Android connection response.')}
 if(!res.ok)throw Error(out.error||'Request failed');return out;
}"""
if settings.count(old)!=1:raise RuntimeError('Settings API request changed; refusing broken APK.')
settings=settings.replace(old,new_req)
settings=settings.replace('Only the local Python server sees the key; it stays in memory until you disconnect or close it.',
 'Your key stays on this Android device. Save it securely here, or leave Save Key unchecked to use it only until you disconnect or close the app.')
settings=settings.replace("'Local server online. Paste your OpenAI API key and select Connect & Test.'",
 "'Android connected. Paste your OpenAI key and select Connect & Test.'")
settings=settings.replace("status.textContent='SERVER OFFLINE';info.textContent='Start the app with start_veilradio.bat. Opening the HTML file alone cannot securely connect the API.'",
 "status.textContent='ANDROID UNAVAILABLE';info.textContent='Could not reach Android services. Restart Veilradio and retry.'")
settings=settings.replace("info.textContent='Disconnected. Key cleared from server memory.'",
 "info.textContent='Disconnected. Your key has been cleared from this device.'")
text=text[:start]+base64.b64encode(settings.encode('utf-8')).decode('ascii')+text[end:]
assert 'window.__veilBridgeRequest=function(' in text
assert "const bridge=window.parent?.__veilBridgeRequest;" in settings
path.write_text(text,encoding='utf-8')
print('Android native API bridge installed and Settings requests fixed.')
