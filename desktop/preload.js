'use strict';
const {contextBridge,ipcRenderer}=require('electron');
contextBridge.exposeInMainWorld('VeilNative',{request(id,path,raw){
 if(!/^api[0-9]{1,10}$/.test(String(id)))return;
 ipcRenderer.invoke('veilradio:request',{path,raw}).then(
  packet=>window.postMessage(JSON.stringify({type:'veilradio:reply',id,packet}),'*'),
  err=>window.postMessage(JSON.stringify({type:'veilradio:reply',id,packet:{status:503,body:{error:err.message||'Desktop request failed.'}}}),'*')
 );
}});
