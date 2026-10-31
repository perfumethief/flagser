package com.veilradio.android;
import android.content.*;import android.webkit.*;import android.security.keystore.*;import android.util.Base64;
import org.json.*;import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.security.KeyStore;
import java.util.*;import java.util.concurrent.*;import javax.crypto.*;import javax.crypto.spec.GCMParameterSpec;
public final class NativeService{
 private static final String MODEL="gpt-4.1-mini",ALIAS="veilradio-aes-key-v1",SAVED="encrypted-openai-key";
 private final SharedPreferences prefs;private final ExecutorService workers=Executors.newFixedThreadPool(3);
 private volatile WebView web;private volatile String key;
 public NativeService(Context c){prefs=c.getApplicationContext().getSharedPreferences("veilradio",Context.MODE_PRIVATE);
 try{key=decrypt(prefs.getString(SAVED,""));}catch(Exception e){key=null;prefs.edit().remove(SAVED).apply();}}
 public void attach(WebView w){web=w;}public void shutdown(){workers.shutdownNow();key=null;}
 @JavascriptInterface public void request(String id,String path,String raw){
 if(id==null||!id.matches("api[0-9]{1,10}"))return;
 workers.execute(()->{JSONObject packet;try{JSONObject data=new JSONObject(raw==null?"{}":raw);
 packet=new JSONObject().put("status",200).put("body",route(path,data));}
 catch(ApiError e){packet=error(e.code,e.getMessage());}
 catch(Exception e){packet=error(503,"Request failed. Check internet connection.");}
 final String reply=packet.toString();WebView w=web;
 if(w!=null)w.post(()->w.evaluateJavascript("window.__veilBridgeDeliver("+JSONObject.quote(id)+","+JSONObject.quote(reply)+");",null));});}
 private static JSONObject error(int code,String msg){try{return new JSONObject().put("status",code).put("body",new JSONObject().put("error",msg));}
 catch(Exception e){return new JSONObject();}}
 private static String str(JSONObject o,String k){return o.optString(k,"").trim();}
 private JSONObject route(String path,JSONObject data)throws Exception{
 if(path==null||!path.startsWith("/api/"))throw new ApiError(404,"Unknown endpoint.");
 if(path.equals("/api/status"))return new JSONObject().put("connected",key!=null&&!key.isEmpty())
 .put("saved",prefs.contains(SAVED)).put("model",MODEL);
 if(path.equals("/api/connect")){String k=str(data,"key");
 if(!k.startsWith("sk-")||k.length()<20||k.length()>450)throw new ApiError(400,"Enter a valid OpenAI API key.");
 if(!ai(k,"Return JSON {\"ok\":true}.","Connection check.",28).optBoolean("ok",false))throw new ApiError(401,"Key connection failed.");
 boolean remember=data.optBoolean("remember",false);
 if(remember){try{if(!prefs.edit().putString(SAVED,encrypt(k)).commit())throw new IOException();}
 catch(Exception e){throw new ApiError(500,"Couldn't encrypt and save key.");}}
 else prefs.edit().remove(SAVED).apply();
 key=k;return new JSONObject().put("connected",true).put("saved",remember).put("model",MODEL);}
 if(path.equals("/api/disconnect")){key=null;prefs.edit().remove(SAVED).apply();return new JSONObject().put("connected",false).put("saved",false);}
 if(path.startsWith("/api/deezer/search?")){
 int limit=10;try{limit=Integer.parseInt(param(path,"limit"));}catch(Exception ignored){}
 return new JSONObject().put("tracks",search(param(path,"q"),limit));}
 if(path.startsWith("/api/deezer/track?")){String id=param(path,"id");
 if(!id.matches("[0-9]{1,16}"))throw new ApiError(400,"Invalid track ID.");
 return new JSONObject().put("track",track(id));}
 if(path.equals("/api/discover"))return discover(data);
 if(path.equals("/api/drift"))return drift(data);
 throw new ApiError(404,"Unknown API endpoint.");
 }
 private String param(String url,String key)throws Exception{int i=url.indexOf('?');if(i<0)return "";
 for(String p:url.substring(i+1).split("&")){int k=p.indexOf('=');if(k>0&&p.substring(0,k).equals(key))
 return URLDecoder.decode(p.substring(k+1),"UTF-8");}return "";}
 private JSONObject http(String url,JSONObject payload,String token,int timeout)throws Exception{
 HttpURLConnection c=null;try{
 c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(timeout);
 c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","Veilradio-Android/0.5.13");
 if(token!=null)c.setRequestProperty("Authorization","Bearer "+token);
 if(payload!=null){c.setDoOutput(true);c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/json");
 try(OutputStream out=c.getOutputStream()){out.write(payload.toString().getBytes(StandardCharsets.UTF_8));}}
 int status=c.getResponseCode();InputStream in=status>=400?c.getErrorStream():c.getInputStream();
 if(in==null)throw new ApiError(502,"Provider returned no response.");
 ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
 try(InputStream input=in){while((n=input.read(buf))!=-1){out.write(buf,0,n);if(out.size()>1500000)throw new ApiError(502,"Reply too large.");}}
 JSONObject result=new JSONObject(out.toString("UTF-8"));
 if(status>=400){JSONObject er=result.optJSONObject("error");String msg=er==null?"":er.optString("message","");
 if(status==401)throw new ApiError(401,"Invalid OpenAI API key.");if(status==429)throw new ApiError(429,"Rate limit or credits reached.");
 throw new ApiError(status==404?404:502,msg.isEmpty()?"Service error "+status:msg);}
 return result;}catch(ApiError e){throw e;}catch(IOException e){throw new ApiError(503,"Cannot reach service. Check internet.");}
 finally{if(c!=null)c.disconnect();}}
 private JSONObject ai(String token,String system,String prompt,int limit)throws Exception{
 JSONArray m=new JSONArray().put(new JSONObject().put("role","system").put("content",system))
 .put(new JSONObject().put("role","user").put("content",prompt));
 JSONObject req=new JSONObject().put("model",MODEL).put("messages",m).put("max_tokens",limit)
 .put("response_format",new JSONObject().put("type","json_object")).put("temperature",0.85);
 JSONObject response=http("https://api.openai.com/v1/chat/completions",req,token,45000);
 try{return new JSONObject(response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"));}
 catch(JSONException e){throw new ApiError(502,"AI response was not valid JSON.");}}
 private JSONObject parse(JSONObject o)throws Exception{
 if(o==null)return null;JSONObject a=o.optJSONObject("artist"),al=o.optJSONObject("album");
 if(a==null||!o.has("id")||str(o,"title").isEmpty()||str(a,"name").isEmpty())return null;
 String id=o.optString("id");
 return new JSONObject().put("id",id).put("title",o.optString("title")).put("artist",a.optString("name"))
 .put("album",al==null?"":al.optString("title")).put("preview",o.optString("preview",""))
 .put("url","https://www.deezer.com/track/"+id).put("cover",al==null?"":al.optString("cover_medium"));}
 private JSONArray search(String q,int limit)throws Exception{
 if(q.trim().isEmpty())return new JSONArray();if(q.length()>160)q=q.substring(0,160);
 JSONObject data=http("https://api.deezer.com/search?q="+URLEncoder.encode(q,"UTF-8")+"&limit="+Math.max(1,Math.min(limit,30)),null,null,16000);
 if(data.has("error"))throw new ApiError(503,"Deezer catalogue is unavailable.");
 JSONArray out=new JSONArray(),rows=data.optJSONArray("data");
 if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject t=parse(rows.optJSONObject(i));if(t!=null)out.put(t);}
 return out;}
 private JSONObject track(String id)throws Exception{
 JSONObject r=http("https://api.deezer.com/track/"+id,null,null,16000);
 if(r.has("error"))throw new ApiError(404,"Deezer track unavailable.");
 JSONObject t=parse(r);if(t==null)throw new ApiError(404,"Track unavailable.");return t;}
 private static String norm(String s){return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
 private static double similar(String aa,String bb){
 String a=norm(aa),b=norm(bb);if(a.equals(b))return 1;
 int[] p=new int[b.length()+1],q=new int[b.length()+1];for(int j=0;j<=b.length();j++)p[j]=j;
 for(int i=1;i<=a.length();i++){q[0]=i;for(int j=1;j<=b.length();j++)q[j]=Math.min(Math.min(q[j-1]+1,p[j]+1),p[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));
 int[] t=p;p=q;q=t;}return 1-(double)p[b.length()]/Math.max(1,Math.max(a.length(),b.length()));}
 private JSONObject verify(JSONObject idea){if(idea==null)return null;
 String artist=str(idea,"artist"),title=str(idea,"title");if(artist.isEmpty()||title.isEmpty())return null;
 try{JSONArray rows=search(artist+" "+title,14);JSONObject best=null;double score=0;
 for(int i=0;i<rows.length();i++){JSONObject t=rows.optJSONObject(i);if(t==null||str(t,"preview").isEmpty())continue;
 double a=similar(artist,str(t,"artist")),b=similar(title,str(t,"title"));
 if(a>=.88&&b>=.86&&a+b>score){score=a+b;best=t;}}
 if(best!=null){best.put("reason",str(idea,"reason"));return best;}}
 catch(Exception ignored){}return null;}
 private JSONObject discover(JSONObject data)throws Exception{
 if(key==null||key.isEmpty())throw new ApiError(401,"Connect your OpenAI key in Settings / API.");
 String mode=data.optString("mode","signal"),signal=str(data,"signal"),seed=str(data,"seed");
 if(!Arrays.asList("signal","seed","drift","artists").contains(mode))throw new ApiError(400,"Unsupported discovery mode.");
 if(signal.isEmpty()&&seed.isEmpty())throw new ApiError(400,"Add an artist, song or sound description.");
 if(signal.length()>500)signal=signal.substring(0,500);if(seed.length()>140)seed=seed.substring(0,140);
 JSONObject recent=data.optJSONObject("recent"),ex=data.optJSONObject("exclude");
 JSONArray loved=recent==null?null:recent.optJSONArray("love"),passed=recent==null?null:recent.optJSONArray("pass");
 if(loved==null)loved=new JSONArray();if(passed==null)passed=new JSONArray();
 String system="You curate underground-leaning real released music. Return JSON with candidates array of 18 objects "+
 "each with artist,title,reason strings. No invented tracks, no duplicates. Pay attention to vibe, melodic vocals, rhythm and texture.";
 String prompt=mode.equals("artists")?
 "Radio blending the listener's preferred artists: "+signal+". Recommend connected artists, influences, collaborations and compatible tracks.":
 "Mode "+mode+". Signal "+signal+". Starting artist, song, or concept "+seed+".";
 prompt+=" Loved: "+loved+". Passed: "+passed+". Avoid categorized songs. Return 18 new real tracks.";
 JSONArray ideas=ai(key,system,prompt,1900).optJSONArray("candidates");
 if(ideas==null)throw new ApiError(502,"AI returned no music suggestions.");
 Set<String> ids=new HashSet<>(),names=new HashSet<>(),seen=new HashSet<>();
 if(ex!=null){JSONArray a=ex.optJSONArray("ids"),b=ex.optJSONArray("names");
 if(a!=null)for(int i=0;i<a.length();i++)ids.add(a.optString(i));
 if(b!=null)for(int i=0;i<b.length();i++)names.add(b.optString(i));}
 JSONArray found=new JSONArray();ExecutorService pool=Executors.newFixedThreadPool(4);
 try{List<Future<JSONObject>> futures=new ArrayList<>();
 for(int i=0;i<Math.min(18,ideas.length());i++){JSONObject idea=ideas.optJSONObject(i);
 if(idea!=null)futures.add(pool.submit(()->verify(idea)));}
 for(Future<JSONObject> f:futures){JSONObject t;try{t=f.get(75,TimeUnit.SECONDS);}catch(Exception e){continue;}
 if(t==null)continue;String id=t.optString("id"),name=norm(t.optString("artist"))+":"+norm(t.optString("title"));
 if(ids.contains(id)||names.contains(name)||!seen.add(id))continue;found.put(t);if(found.length()==9)break;}}
 finally{pool.shutdownNow();}
 if(found.length()==0)throw new ApiError(422,"No verified Deezer previews found. Try another seed.");
 return new JSONObject().put("tracks",found).put("verified",found.length()).put("proposed",Math.min(18,ideas.length()))
 .put("mode",mode).put("source","DEEZER VERIFIED PREVIEWS");}
 private JSONObject drift(JSONObject p)throws Exception{
 if(key==null||key.isEmpty())throw new ApiError(401,"Connect your OpenAI key.");
 String node=str(p,"node");if(node.isEmpty())node="melodic rap";
 JSONObject o=ai(key,"Return JSON with kind,note and six links array. Each link has name,type,why. Links must be real related artists, tracks or genres.",
 "Explore music connected to "+node+". Trail "+p.optJSONArray("trail"),600);
 JSONArray src=o.optJSONArray("links"),out=new JSONArray();
 if(src!=null)for(int i=0;i<Math.min(8,src.length());i++){JSONObject a=src.optJSONObject(i);
 if(a!=null&&!str(a,"name").isEmpty())out.put(new JSONObject().put("name",str(a,"name")).put("type",str(a,"type")).put("why",str(a,"why")));}
 if(out.length()==0)throw new ApiError(422,"No links returned.");
 return new JSONObject().put("node",node).put("kind",o.optString("kind","MUSIC")).put("note",o.optString("note","")).put("links",out);}
 private SecretKey secret()throws Exception{
 KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);KeyStore.Entry e=ks.getEntry(ALIAS,null);
 if(e instanceof KeyStore.SecretKeyEntry)return ((KeyStore.SecretKeyEntry)e).getSecretKey();
 KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
 g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
 .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
 return g.generateKey();}
 private String encrypt(String k)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
 c.init(Cipher.ENCRYPT_MODE,secret());byte[] iv=c.getIV(),body=c.doFinal(k.getBytes(StandardCharsets.UTF_8));
 byte[] out=new byte[iv.length+body.length];System.arraycopy(iv,0,out,0,iv.length);System.arraycopy(body,0,out,iv.length,body.length);
 return Base64.encodeToString(out,Base64.NO_WRAP);}
 private String decrypt(String input)throws Exception{if(input==null||input.isEmpty())return null;
 byte[] x=Base64.decode(input,Base64.NO_WRAP);if(x.length<30)throw new IOException("Invalid key blob");
 Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,secret(),new GCMParameterSpec(128,Arrays.copyOfRange(x,0,12)));
 return new String(c.doFinal(x,12,x.length-12),StandardCharsets.UTF_8);}
 private static final class ApiError extends Exception{final int code;ApiError(int s,String m){super(m);code=s;}}
}
