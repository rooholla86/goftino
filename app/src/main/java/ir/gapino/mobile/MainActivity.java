package ir.gapino.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int PICK_IMAGE = 801;
    private static final int ASK_CAMERA = 802;
    private static final String PREF_URL = "server_url";
    private WebView web;
    private FrameLayout root;
    private ProgressBar progress;
    private String serverUrl;
    private android.webkit.ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingCamera;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(8,101,157));
        getWindow().setNavigationBarColor(Color.rgb(8,101,157));
        serverUrl = getPreferences(MODE_PRIVATE).getString(PREF_URL, "");
        if (serverUrl.isEmpty()) showSetup(); else openChat();
    }

    private int dp(int px) {
        return (int)(px * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showSetup() {
        destroyWeb();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(24), dp(24), dp(24), dp(24));
        panel.setBackgroundColor(0xfff5f9fc);
        TextView title = new TextView(this);
        title.setText("گپینو");
        title.setTextSize(32);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(0xff08659d);
        panel.addView(title);
        TextView info = new TextView(this);
        info.setText("آدرس امن HTTPS سرور گپینو را بنویسید تا گفتگوها باز شوند.");
        info.setTextColor(0xff254660);
        info.setTextSize(16);
        info.setPadding(0,dp(18),0,dp(12));
        panel.addView(info);
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setTextDirection(View.TEXT_DIRECTION_LTR);
        field.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        field.setHint("https://your-domain.com/gapino/public/");
        field.setText(serverUrl);
        panel.addView(field, new LinearLayout.LayoutParams(-1,dp(55)));
        Button connect = new Button(this);
        connect.setText("اتصال به گپینو");
        panel.addView(connect, new LinearLayout.LayoutParams(-1,dp(56)));
        TextView note = new TextView(this);
        note.setText("نسخه موبایل به سرور PHP/MySQL موجود وصل می‌شود. برای ارسال و دریافت پیام، سرور باید فعال و دارای HTTPS معتبر باشد.");
        note.setTextSize(13);
        note.setPadding(0,dp(22),0,0);
        panel.addView(note);
        setContentView(panel);
        connect.setOnClickListener(v -> {
            String url = validUrl(field.getText().toString());
            if (url == null) { field.setError("آدرس HTTPS معتبر وارد کنید"); return; }
            serverUrl = url;
            getPreferences(MODE_PRIVATE).edit().putString(PREF_URL,serverUrl).apply();
            openChat();
        });
    }

    private String validUrl(String text) {
        try {
            Uri uri = Uri.parse(text.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
                uri.getHost().trim().isEmpty() || uri.getUserInfo() != null ||
                uri.getQuery() != null || uri.getFragment() != null) return null;
            String path = uri.getEncodedPath();
            if (path == null || path.isEmpty()) path = "/";
            if (!path.endsWith("/")) path += "/";
            return new Uri.Builder().scheme("https")
                .encodedAuthority(uri.getEncodedAuthority()).encodedPath(path).build().toString();
        } catch (Exception ex) { return null; }
    }

    private boolean allowed(Uri uri) {
        try {
            Uri base = Uri.parse(serverUrl);
            return "https".equalsIgnoreCase(uri.getScheme()) &&
                base.getHost().equalsIgnoreCase(uri.getHost()) &&
                base.getPort() == uri.getPort() &&
                uri.getEncodedPath() != null &&
                uri.getEncodedPath().startsWith(base.getEncodedPath());
        } catch (Exception ex) { return false; }
    }

    private boolean navigate(Uri uri) {
        if (allowed(uri)) return false;
        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme) ||
            "mailto".equalsIgnoreCase(scheme) || "tel".equalsIgnoreCase(scheme)) {
            try { startActivity(new Intent(Intent.ACTION_VIEW,uri)); }
            catch (ActivityNotFoundException ignored) { }
        }
        return true;
    }

    private void openChat() {
        destroyWeb();
        root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1,-1));
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(-1,dp(3),Gravity.TOP);
        root.addView(progress,progressParams);
        setContentView(root);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setBuiltInZoomControls(false);
        if (android.os.Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                return !req.isForMainFrame() ? false : navigate(req.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return navigate(Uri.parse(url));
            }
            @Override public void onPageFinished(WebView v, String url) {
                CookieManager.getInstance().flush();
            }
            @Override public void onReceivedSslError(WebView v, SslErrorHandler h, android.net.http.SslError e) {
                h.cancel();
                showError();
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError e) {
                if (req.isForMainFrame()) showError();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                if (progress != null) {
                    progress.setProgress(p);
                    progress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
                }
            }
            @Override public boolean onShowFileChooser(WebView v,
                android.webkit.ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
                    picker.addCategory(Intent.CATEGORY_OPENABLE);
                    picker.setType("image/*");
                    picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                        params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                    startActivityForResult(Intent.createChooser(picker,"انتخاب تصویر"),PICK_IMAGE);
                } catch (ActivityNotFoundException e) {
                    fileCallback.onReceiveValue(null);
                    fileCallback = null;
                    Toast.makeText(MainActivity.this,"انتخاب تصویر ممکن نیست",Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest req) {
                runOnUiThread(() -> {
                    boolean cameraOnly = req.getResources().length == 1 &&
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(req.getResources()[0]);
                    if (!allowed(req.getOrigin()) || !cameraOnly) { req.deny(); return; }
                    if (checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
                        req.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    else {
                        pendingCamera=req;
                        requestPermissions(new String[]{Manifest.permission.CAMERA},ASK_CAMERA);
                    }
                });
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest req) {
                if (pendingCamera==req) pendingCamera=null;
            }
        });

        web.setDownloadListener((url,agent,disposition,mime,size) -> {
            try {
                if (!allowed(Uri.parse(url))) {
                    Toast.makeText(this,"دانلود فقط از سرور گپینو مجاز است",Toast.LENGTH_SHORT).show();
                    return;
                }
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                String cookie=CookieManager.getInstance().getCookie(serverUrl);
                if (cookie!=null) r.addRequestHeader("Cookie",cookie);
                if (agent!=null) r.addRequestHeader("User-Agent",agent);
                String name=android.webkit.URLUtil.guessFileName(url,disposition,mime);
                r.setTitle(name);
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalFilesDir(this,Environment.DIRECTORY_DOWNLOADS,name);
                DownloadManager manager=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
                if (manager!=null) manager.enqueue(r);
            } catch (Exception ex) {
                Toast.makeText(this,"دانلود انجام نشد",Toast.LENGTH_SHORT).show();
            }
        });
        web.loadUrl(serverUrl);
    }

    private void showError() {
        if (isFinishing() || root==null) return;
        new AlertDialog.Builder(this)
            .setTitle("اتصال به گپینو برقرار نشد")
            .setMessage("اینترنت، آدرس سرور و گواهی HTTPS را بررسی کنید.")
            .setPositiveButton("تلاش دوباره",(d,w)->{if(web!=null)web.reload();})
            .setNeutralButton("تغییر سرور",(d,w)->showSetup())
            .setNegativeButton("بستن",null).show();
    }

    @Override public void onRequestPermissionsResult(int code,String[] p,int[] result) {
        super.onRequestPermissionsResult(code,p,result);
        if (code==ASK_CAMERA && pendingCamera!=null) {
            PermissionRequest req=pendingCamera;
            pendingCamera=null;
            if (result.length>0 && result[0]==PackageManager.PERMISSION_GRANTED && allowed(req.getOrigin()))
                req.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            else req.deny();
        }
    }
    @Override protected void onActivityResult(int code,int result,Intent data) {
        super.onActivityResult(code,result,data);
        if (code!=PICK_IMAGE || fileCallback==null) return;
        Uri[] images=null;
        if (result==RESULT_OK && data!=null) {
            if (data.getClipData()!=null) {
                int count=data.getClipData().getItemCount();
                images=new Uri[count];
                for (int i=0;i<count;i++) images[i]=data.getClipData().getItemAt(i).getUri();
            } else if(data.getData()!=null) images=new Uri[]{data.getData()};
        }
        fileCallback.onReceiveValue(images);
        fileCallback=null;
    }

    @Override public void onBackPressed() {
        if (web!=null && web.canGoBack()) { web.goBack(); return; }
        new AlertDialog.Builder(this).setTitle("گپینو")
            .setItems(new String[]{"بازگشت","تغییر آدرس سرور","خروج"},(d,which)->{
                if(which==1)showSetup();
                else if(which==2)finish();
            }).show();
    }

    private void destroyWeb() {
        if (fileCallback!=null) { fileCallback.onReceiveValue(null); fileCallback=null; }
        if (pendingCamera!=null) { pendingCamera.deny(); pendingCamera=null; }
        if(web!=null) {
            web.stopLoading();
            if(web.getParent() instanceof ViewGroup)((ViewGroup)web.getParent()).removeView(web);
            web.destroy();
            web=null;
        }
        progress=null;
        root=null;
    }
    @Override protected void onDestroy() { destroyWeb(); super.onDestroy(); }
}
