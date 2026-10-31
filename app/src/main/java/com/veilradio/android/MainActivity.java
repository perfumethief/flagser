package com.veilradio.android;
import android.app.*;import android.os.*;import android.net.*;import android.content.*;import android.content.res.*;import android.webkit.*;import android.view.*;import androidx.webkit.WebViewAssetLoader;
public class MainActivity extends Activity{
 private static final String SETTINGS_FRAME_FIX="(function(){\nif(window.__veilSettingsBridgeInstalled)return;\nwindow.__veilSettingsBridgeInstalled=true;\nvar meta=document.querySelector('meta[name=\"viewport\"]');\nif(meta)meta.setAttribute('content','width=device-width,initial-scale=1,minimum-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover');\nfunction wire(frame){\n if(!frame||frame.dataset.veilApiAttached==='1')return;\n frame.dataset.veilApiAttached='1';\n function patch(){\n  try{\n   var inner=frame.contentWindow;\n   if(!inner||inner.__veilApiPatched||!window.__veilBridgeRequest)return;\n   var original=inner.fetch.bind(inner);\n   inner.fetch=function(input,options){\n    var raw=typeof input==='string'?input:(input&&input.url)||'';\n    try{\n      var u=new URL(raw,document.baseURI);\n      if(u.pathname.indexOf('/api/')===0)return window.__veilBridgeRequest(u.pathname+u.search,options||{});\n    }catch(e){}\n    return original(input,options);\n   };\n   inner.__veilApiPatched=true;\n  }catch(e){}\n }\n frame.addEventListener('load',patch);\n patch();\n}\nfunction install(){wire(document.getElementById('vSettingsFrame'));}\nnew MutationObserver(install).observe(document.documentElement,{childList:true,subtree:true});\ninstall();\n})();";
 private static final int PICK=6401;private WebView web;private NativeService bridge;private ValueCallback<Uri[]> callback;
 @Override public void onCreate(Bundle state){super.onCreate(state);
 immersive();
 bridge=new NativeService(this);
 WebViewAssetLoader assets=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
 web=new WebView(this);web.setBackgroundColor(0xffe8e1e5);web.setOverScrollMode(View.OVER_SCROLL_NEVER);
 WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
 settings.setAllowFileAccess(false);settings.setAllowContentAccess(true);
 settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setMediaPlaybackRequiresUserGesture(false);
 settings.setSupportZoom(false);settings.setBuiltInZoomControls(false);settings.setDisplayZoomControls(false);settings.setTextZoom(100);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);
 web.addJavascriptInterface(bridge,"VeilNative");
 web.setWebViewClient(new WebViewClient(){
 @Override public void onPageFinished(WebView v,String url){if(url!=null&&url.startsWith("https://appassets.androidplatform.net/assets/"))v.evaluateJavascript(SETTINGS_FRAME_FIX,null);}
 @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return assets.shouldInterceptRequest(r.getUrl());}
 @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
 Uri u=r.getUrl();if(u.getHost()!=null&&u.getHost().equals("appassets.androidplatform.net"))return false;
 try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}return true;}});
 web.setWebChromeClient(new WebChromeClient(){
 @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams params){
 if(callback!=null)callback.onReceiveValue(null);callback=cb;
 try{startActivityForResult(params.createIntent(),PICK);}catch(Exception e){callback.onReceiveValue(null);callback=null;return false;}return true;}});
 bridge.attach(web);setContentView(web);web.loadUrl("https://appassets.androidplatform.net/assets/veilradio.html");
 }
 private void immersive(){
 getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
 if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);WindowInsetsController c=getWindow().getInsetsController();if(c!=null){c.hide(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}}
 }
 @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)immersive();}
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);
 if(req==PICK&&callback!=null){callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));callback=null;}}
 @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
 @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);}
 @Override protected void onDestroy(){if(web!=null){web.removeJavascriptInterface("VeilNative");web.destroy();}
 if(bridge!=null)bridge.shutdown();super.onDestroy();}
}