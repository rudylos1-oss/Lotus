<LOCAL_FILE:/home/daytona/workspace/Lotus/Lotus/app/src/main/java/nl/rudy/lotus/MainActivity.java>package nl.rudy.lotus;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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
    private static final int LOCATION_REQUEST = 71;
    private static final int PICK_MEDIA = 72;
    private static final int TAKE_PHOTO = 73;
    private static final int TAKE_VIDEO = 74;
    private static final int RECORD_AUDIO = 75;
    private static final int CAMERA_PHOTO_REQUEST = 77;
    private static final int CAMERA_VIDEO_REQUEST = 78;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingLocationRequestId = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        createChannel();
        web = new WebView(this);
        setContentView(web);
        configureWebView();
        web.loadUrl("file:///android_asset/index.html");
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void configureWebView() {
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(true);
        web.getSettings().setAllowFileAccess(false);
        web.addJavascriptInterface(new LotusBridge(), "LotusAndroid");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                fileCallback = callback;
                chooseGallery();
                return true;
            }
        });
    }

    private void chooseGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*", "audio/*"});
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_MEDIA);
    }

    private void openCameraChooser() {
        new AlertDialog.Builder(this).setTitle("Camera").setMessage("Wat wil je maken?")
                .setPositiveButton("Foto", (dialog, which) -> openCameraForPhoto())
                .setNegativeButton("Video", (dialog, which) -> openCameraForVideo())
                .setNeutralButton("Annuleren", null).show();
    }

    private void openCameraForPhoto() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PHOTO_REQUEST);
            return;
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) startActivityForResult(intent, TAKE_PHOTO);
        else Toast.makeText(this, "Geen camera-app beschikbaar", Toast.LENGTH_SHORT).show();
    }

    private void openCameraForVideo() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_VIDEO_REQUEST);
            return;
        }
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
        if (intent.resolveActivity(getPackageManager()) != null) startActivityForResult(intent, TAKE_VIDEO);
        else Toast.makeText(this, "Geen camera-app beschikbaar", Toast.LENGTH_SHORT).show();
    }

    private void recordAudio() {
        Intent intent = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
        if (intent.resolveActivity(getPackageManager()) != null) startActivityForResult(intent, RECORD_AUDIO);
        else Toast.makeText(this, "Geen opname-app beschikbaar", Toast.LENGTH_SHORT).show();
    }

    private void requestLocation(String requestId) {
        pendingLocationRequestId = requestId == null ? "" : requestId;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST);
            return;
        }
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location last = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (last == null) last = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (last != null) sendLocation(last, pendingLocationRequestId);
        try {
            manager.requestSingleUpdate(LocationManager.GPS_PROVIDER, new LocationListener() {
                @Override public void onLocationChanged(Location location) { sendLocation(location, pendingLocationRequestId); }
                @Override public void onProviderEnabled(String provider) { }
                @Override public void onProviderDisabled(String provider) { }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
            }, null);
        } catch (Exception error) {
            if (last == null) js("window.LotusNativeLocation&&LotusNativeLocation(null,'" + jsQuote(pendingLocationRequestId) + "')");
        }
    }

    private void sendLocation(Location location, String requestId) {
        String point = String.format(java.util.Locale.US, "{lat:%.6f,lon:%.6f,accuracy:%.0f,time:%d}", location.getLatitude(), location.getLongitude(), location.getAccuracy(), location.getTime());
        js("window.LotusNativeLocation&&LotusNativeLocation(" + point + ",'" + jsQuote(requestId) + "')");
    }

    private void openNavigation(double lat, double lon) {
        Uri geo = Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon + "(Lotus locatie)");
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, geo), "Open locatie met"));
    }

    private void createChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel("lotus_messages", "Lotus berichten", NotificationManager.IMPORTANCE_HIGH);
        channel.enableLights(true);
        channel.setLightColor(Color.MAGENTA);
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (request == LOCATION_REQUEST) {
            if (granted) requestLocation(pendingLocationRequestId);
            else Toast.makeText(this, "Locatie delen is niet toegestaan.", Toast.LENGTH_LONG).show();
        } else if (request == CAMERA_PHOTO_REQUEST) {
            if (granted) openCameraForPhoto();
            else Toast.makeText(this, "Camera-toegang is nodig om een foto te maken.", Toast.LENGTH_LONG).show();
        } else if (request == CAMERA_VIDEO_REQUEST) {
            if (granted) openCameraForVideo();
            else Toast.makeText(this, "Camera-toegang is nodig om een video te maken.", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (result != RESULT_OK) {
            if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
            return;
        }
        Uri uri = data == null ? null : data.getData();
        if (code == PICK_MEDIA && fileCallback != null) {
            fileCallback.onReceiveValue(uri == null ? null : new Uri[]{uri});
            fileCallback = null;
            return;
        }
        if (code == TAKE_PHOTO && data != null && data.getExtras() != null) {
            android.graphics.Bitmap bitmap = (android.graphics.Bitmap) data.getExtras().get("data");
            if (bitmap != null) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, output);
                sendBytes(output.toByteArray(), "image/jpeg", "camera.jpg");
            }
            return;
        }
        if (uri != null) readAndSend(uri, code);
    }

    private void readAndSend(Uri uri, int code) {
        try {
            InputStream input = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
            input.close();
            String mime = getContentResolver().getType(uri);
            if (mime == null) mime = code == TAKE_VIDEO ? "video/mp4" : "application/octet-stream";
            String name = mime.startsWith("video/") ? "video.mp4" : (mime.startsWith("image/") ? "foto.jpg" : "spraakbericht.m4a");
            sendBytes(output.toByteArray(), mime, name);
        } catch (Exception error) {
            Toast.makeText(this, "Media kon niet worden gelezen", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendBytes(byte[] bytes, String mime, String name) {
        savePrivateBytes(bytes, name);
        String encoded = Base64.getEncoder().encodeToString(bytes);
        js("window.LotusNativeMedia&&LotusNativeMedia('data:" + mime + ";base64," + encoded + "','" + jsQuote(name) + "')");
    }

    private void savePrivateBytes(byte[] bytes, String filename) {
        try {
            File folder = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Lotus");
            folder.mkdirs();
            new File(folder, ".nomedia").createNewFile();
            File output = new File(folder, filename.replaceAll("[^a-zA-Z0-9._-]", "_"));
            FileOutputStream stream = new FileOutputStream(output);
            stream.write(bytes);
            stream.close();
        } catch (Exception ignored) { }
    }

    private String jsQuote(String value) { return value.replace("\\", "\\\\").replace("'", "\\'"); }
    private void js(String script) { web.post(() -> web.evaluateJavascript(script, null)); }

    public final class LotusBridge {
        @JavascriptInterface public void requestMyLocation() { runOnUiThread(() -> requestLocation("")); }
        @JavascriptInterface public void requestMyLocationFor(String requestId) { runOnUiThread(() -> requestLocation(requestId)); }
        @JavascriptInterface public void openNavigation(String latitude, String longitude) {
            runOnUiThread(() -> { try { openNavigation(Double.parseDouble(latitude), Double.parseDouble(longitude)); } catch (Exception error) { Toast.makeText(MainActivity.this, "Locatie is niet geldig", Toast.LENGTH_SHORT).show(); } });
        }
        @JavascriptInterface public void openGallery() { runOnUiThread(() -> chooseGallery()); }
        @JavascriptInterface public void openCamera() { runOnUiThread(() -> openCameraChooser()); }
        @JavascriptInterface public void recordVoice() { runOnUiThread(() -> recordAudio()); }
        @JavascriptInterface public void notify(String title, String body) { notifyKind(title, body, "message"); }
        @JavascriptInterface public void notifyKind(String title, String body, String kind) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            Notification.Builder builder = new Notification.Builder(MainActivity.this, "lotus_messages").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setAutoCancel(true);
            if ("checkin".equals(kind) || "task".equals(kind) || "location".equals(kind)) builder.setDefaults(Notification.DEFAULT_ALL);
            manager.notify((int) (System.currentTimeMillis() & 0xfffffff), builder.build());
        }
    }
}
<LOCAL_FILE:/home/daytona/workspace/Lotus/Lotus/app/src/main/java/nl/rudy/lotus/MainActivity.java><LOCAL_FILE:/home/daytona/workspace/Lotus/Lotus/app/src/main/java/nl/rudy/lotus/MainActivity.java><LOCAL_FILE:/home/daytona/workspace/Lotus/Lotus/app/src/main/java/nl/rudy/lotus/MainActivity.java><LOCAL_FILE:/home/daytona/workspace/Lotus/Lotus/app/src/main/java/nl/rudy/lotus/MainActivity.java>package nl.rudy.lotus;


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
