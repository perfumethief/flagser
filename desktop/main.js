'use strict';
const {app,BrowserWindow,ipcMain,safeStorage,shell}=require('electron');
const path=require('node:path'),fs=require('node:fs'),api=require('./api');
let windowMain,key='';
const keyPath=()=>path.join(app.getPath('userData'),'veilradio-openai-key.dat');
function restoreKey(){try{if(fs.existsSync(keyPath())&&safeStorage.isEncryptionAvailable())key=safeStorage.decryptString(fs.readFileSync(keyPath()));}catch(e){key='';}}
function storeKey(value){
 if(!safeStorage.isEncryptionAvailable())throw new api.ApiError(500,'Windows encryption is unavailable. Uncheck Save Key to continue without saving.');
 fs.mkdirSync(path.dirname(keyPath()),{recursive:true});
 fs.writeFileSync(keyPath(),safeStorage.encryptString(value),{mode:0o600});
}
function clearKey(){try{fs.unlinkSync(keyPath());}catch(e){if(e.code!=='ENOENT')throw e;}}
function createWindow(){
 windowMain=new BrowserWindow({
  width:1440,height:900,minWidth:920,minHeight:660,backgroundColor:'#ede7eb',
  title:'veilradio.',icon:path.join(__dirname,'build','icon.ico'),autoHideMenuBar:true,
  webPreferences:{preload:path.join(__dirname,'preload.js'),nodeIntegration:false,
   contextIsolation:true,sandbox:true,webSecurity:true,backgroundThrottling:false,
   spellcheck:false,partition:'persist:veilradio'}
 });
 windowMain.removeMenu();
 windowMain.webContents.setWindowOpenHandler(({url})=>{
  if(/^https:\/\//.test(url))shell.openExternal(url);return {action:'deny'};
 });
 windowMain.webContents.on('will-navigate',(event,url)=>{
  if(!url.startsWith('file://')){event.preventDefault();if(/^https:\/\//.test(url))shell.openExternal(url);}
 });
 windowMain.webContents.setVisualZoomLevelLimits(1,1);
 windowMain.loadFile(path.join(__dirname,'assets','veilradio.html'));
}
app.whenReady().then(()=>{
 restoreKey();
 ipcMain.handle('veilradio:request',async(event,{path:rawPath,raw}={})=>{
  try {
   if(event.sender!==windowMain?.webContents)throw new api.ApiError(403,'Unknown app window.');
   const u=new URL(String(rawPath||''),'https://appassets.androidplatform.net/');
   if(u.origin!=='https://appassets.androidplatform.net'||!u.pathname.startsWith('/api/'))throw new api.ApiError(404,'Unknown endpoint.');
   let data={};try{data=JSON.parse(raw||'{}');}catch(e){throw new api.ApiError(400,'Invalid request.');}
   const endpoint=u.pathname;
   if(endpoint==='/api/status')return {status:200,body:{connected:!!key,saved:fs.existsSync(keyPath()),model:api.MODEL}};
   if(endpoint==='/api/connect'){
    const value=String(data.key||'').trim();
    if(!value.startsWith('sk-')||value.length<20||value.length>450)throw new api.ApiError(400,'Enter a valid OpenAI API key.');
    await api.checkKey(value);
    if(data.remember===true)storeKey(value);else clearKey();
    key=value;
    return {status:200,body:{connected:true,saved:data.remember===true,model:api.MODEL}};
   }
   if(endpoint==='/api/disconnect'){key='';clearKey();return {status:200,body:{connected:false,saved:false}};}
   if(endpoint==='/api/deezer/search')return {status:200,body:{tracks:await api.search(u.searchParams.get('q')||'',Number(u.searchParams.get('limit'))||10)}};
   if(endpoint==='/api/deezer/track')return {status:200,body:{track:await api.track(u.searchParams.get('id')||'')}};
   if(!key)throw new api.ApiError(401,'Connect your API key in Settings → API.');
   if(endpoint==='/api/discover')return {status:200,body:await api.discover(data,key)};
   if(endpoint==='/api/drift')return {status:200,body:await api.drift(data,key)};
   throw new api.ApiError(404,'Unknown endpoint.');
  }catch(e){return {status:e.status||503,body:{error:e.message||'The request failed.'}};}
 });
 createWindow();
 app.on('activate',()=>{if(BrowserWindow.getAllWindows().length===0)createWindow();});
});
app.on('window-all-closed',()=>{if(process.platform!=='darwin')app.quit();});