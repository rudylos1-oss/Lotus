<LOCAL_FILE:/home/daytona/workspace/Lotus/Lotus/app/src/main/java/nl/rudy/lotus/MainActivity.java><LOCAL_FILE:/home/daytona/workspace/Lotus/Lotus/app/src/main/java/nl/rudy/lotus/MainActivity.java><LOCAL_FILE:/home/daytona/workspace/Lotus/Lotus/app/src/main/java/nl/rudy/lotus/MainActivity.java>package nl.rudy.lotus;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Base64;


public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 71, PICK_MEDIA = 72, TAKE_PHOTO = 73, TAKE_VIDEO = 74, RECORD_AUDIO = 75, NOTIFY_REQUEST = 76, CAMERA_PHOTO_REQUEST = 77, CAMERA_VIDEO_REQUEST = 78;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private Uri pendingCapture;


    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        createChannel();
        web = new WebView(this); setContentView(web); configureWebView();
        web.loadUrl("file:///android_asset/index.html");
    }


    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void configureWebView() {
        web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(true); web.getSettings().setAllowFileAccess(false);
        web.addJavascriptInterface(new LotusBridge(), "LotusAndroid");
        web.setWebViewClient(new WebViewClient() { @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return true; } });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) { fileCallback=cb; chooseGallery(); return true; }
        });
    }


    private void chooseGallery() { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*","audio/*"}); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,PICK_MEDIA); }
    private void openCameraForPhoto() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PHOTO_REQUEST);
            return;
        }
        takePhoto();
    }
    private void openCameraForVideo() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_VIDEO_REQUEST);
            return;
        }
        takeVideo();
    }
    private void takePhoto() { Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE); startActivityForResult(i,TAKE_PHOTO); }
    private void takeVideo() { Intent i=new Intent(MediaStore.ACTION_VIDEO_CAPTURE); i.putExtra(MediaStore.EXTRA_VIDEO_QUALITY,0); startActivityForResult(i,TAKE_VIDEO); }
    private void recordAudio() { Intent i=new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION); startActivityForResult(i,RECORD_AUDIO); }


    private void createChannel() { NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE); NotificationChannel ch=new NotificationChannel("lotus_messages","Lotus berichten",NotificationManager.IMPORTANCE_HIGH); ch.enableLights(true); ch.setLightColor(Color.MAGENTA); ch.enableVibration(true); nm.createNotificationChannel(ch); }
    private void requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_REQUEST); return; }
        LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
        Location last=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER); if(last==null) last=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if(last!=null) sendLocation(last);
        try { lm.requestSingleUpdate(LocationManager.GPS_PROVIDER,new LocationListener(){public void onLocationChanged(Location l){sendLocation(l);} public void onProviderEnabled(String s){} public void onProviderDisabled(String s){} public void onStatusChanged(String s,int a,Bundle b){}},null); } catch(Exception e) { if(last==null) js("window.LotusNativeLocation&&LotusNativeLocation(null)"); }
    }
    private void sendLocation(Location l) { String p=String.format(java.util.Locale.US,"{lat:%.6f,lon:%.6f,accuracy:%.0f,time:%d}",l.getLatitude(),l.getLongitude(),l.getAccuracy(),l.getTime()); js("window.LotusNativeLocation&&LotusNativeLocation("+p+")"); }
    private void js(String script){ web.post(()->web.evaluateJavascript(script,null)); }


    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (request == LOCATION_REQUEST && granted) requestLocation();
        if (request == CAMERA_PHOTO_REQUEST && granted) takePhoto();
        if (request == CAMERA_VIDEO_REQUEST && granted) takeVideo();
        if ((request == CAMERA_PHOTO_REQUEST || request == CAMERA_VIDEO_REQUEST) && !granted)
            Toast.makeText(this,"Camera-toegang is nodig om een foto of video te maken.",Toast.LENGTH_LONG).show();
    }
    @Override protected void onActivityResult(int code,int result,Intent data) { super.onActivityResult(code,result,data); if(result!=RESULT_OK) { if(fileCallback!=null){fileCallback.onReceiveValue(null);fileCallback=null;} return; } Uri uri=data==null?null:data.getData(); if(code==PICK_MEDIA && fileCallback!=null){fileCallback.onReceiveValue(uri==null?null:new Uri[]{uri});fileCallback=null;return;} if(code==TAKE_PHOTO && data!=null && data.getExtras()!=null){ android.graphics.Bitmap b=(android.graphics.Bitmap)data.getExtras().get("data"); try{ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(android.graphics.Bitmap.CompressFormat.JPEG,88,out); nativeMedia(Base64.getEncoder().encodeToString(out.toByteArray()),"image/jpeg","camera.jpg");}catch(Exception ignored){} return;} if(uri!=null) readAndSend(uri,code); }
    private void readAndSend(Uri uri,int code){ try{InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[16384];int n;while((n=in.read(buf))>0)out.write(buf,0,n);in.close();String type=getContentResolver().getType(uri);if(type==null)type=code==TAKE_VIDEO?"video/mp4":"audio/*";nativeMedia(Base64.getEncoder().encodeToString(out.toByteArray()),type,code==TAKE_VIDEO?"video.mp4":"spraakbericht.m4a");}catch(Exception e){Toast.makeText(this,"Media kon niet worden gelezen",Toast.LENGTH_SHORT).show();} }
    private void nativeMedia(String b64,String mime,String name){ js("window.LotusNativeMedia&&LotusNativeMedia('data:"+mime+";base64,"+b64+"','"+name+"')"); }


    public final class LotusBridge {
        @JavascriptInterface public void requestMyLocation(){runOnUiThread(()->{Toast.makeText(MainActivity.this,"Je locatie wordt nu met Lotus gedeeld.",Toast.LENGTH_SHORT).show();requestLocation();});}
        @JavascriptInterface public void openGallery(){runOnUiThread(()->chooseGallery());}
        @JavascriptInterface public void openCamera(){runOnUiThread(()->openCameraForPhoto());}
        @JavascriptInterface public void openVideoCamera(){runOnUiThread(()->openCameraForVideo());}
