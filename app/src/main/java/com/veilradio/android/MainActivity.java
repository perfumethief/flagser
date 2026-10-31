package com.veilradio.android;
import android.app.*;import android.os.*;import android.net.*;import android.content.*;import android.content.res.*;import android.webkit.*;import android.view.*;import androidx.webkit.WebViewAssetLoader;
public class MainActivity extends Activity{
 private static final int PICK=6401;private WebView web;private NativeService bridge;private ValueCallback<Uri[]> callback;
 @Override public void onCreate(Bundle state){super.onCreate(state);
 getWindow().setStatusBarColor(0xffe8e1e5);getWindow().setNavigationBarColor(0xff463e48);
 getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
 bridge=new NativeService(this);
 WebViewAssetLoader assets=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
 web=new WebView(this);web.setBackgroundColor(0xffe8e1e5);web.setOverScrollMode(View.OVER_SCROLL_NEVER);
 WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
 settings.setAllowFileAccess(false);settings.setAllowContentAccess(true);
 settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setMediaPlaybackRequiresUserGesture(false);
 web.addJavascriptInterface(bridge,"VeilNative");
 web.setWebViewClient(new WebViewClient(){
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
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);
 if(req==PICK&&callback!=null){callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));callback=null;}}
 @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
 @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);}
 @Override protected void onDestroy(){if(web!=null){web.removeJavascriptInterface("VeilNative");web.destroy();}
 if(bridge!=null)bridge.shutdown();super.onDestroy();}
}