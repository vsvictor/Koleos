package com.rev.quic;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;

import com.rev.quick.R;

import org.chromium.base.PackageUtils;
import org.chromium.net.ApiVersion;
import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.UploadDataProviders;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;
import org.chromium.net.impl.JavaCronetProvider;
import org.chromium.net.impl.NativeCronetProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private CronetEngine engine;
    private Executor executor;
    private String mUrl;
    private WebView wvGoogle;
    private File outputFile;

    class SimpleUrlRequestCallback extends UrlRequest.Callback {
        private ByteArrayOutputStream mBytesReceived = new ByteArrayOutputStream();
        private WritableByteChannel mReceiveChannel = Channels.newChannel(mBytesReceived);

        @Override
        public void onRedirectReceived(UrlRequest request, UrlResponseInfo info, String newLocationUrl) {
            Log.i(TAG, "****** onRedirectReceived ******");
            request.followRedirect();
            Log.i(TAG, "Request: "+request.toString());
        }

        @Override
        public void onResponseStarted(UrlRequest request, UrlResponseInfo info) {
            Log.i(TAG, "****** Response Started ******");
            Log.i("HEADERS", "*** Headers Are *** " + info.getAllHeaders());
            Log.i(TAG, "UrlResponseInfo: "+info.toString());
            request.read(ByteBuffer.allocateDirect(32 * 1024));
        }

        @Override
        public void onSucceeded(final UrlRequest request, final UrlResponseInfo info) {
            Log.i("RESPONSE", "****** Request Completed, status code is " + info.getHttpStatusCode() + ", total received bytes is " + info.getReceivedByteCount()+", protocol - "+info.getNegotiatedProtocol());

            final String receivedData = mBytesReceived.toString();
            final String url = info.getUrl();
            final String text = "Completed " + url + " (" + info.getHttpStatusCode() + ")";
            MainActivity.this.runOnUiThread(new Runnable() {
                public void run() {
                    wvGoogle.loadData(receivedData, "text/html", null);

                }
            });
        }

        @Override
        public void onReadCompleted(UrlRequest request, UrlResponseInfo info, ByteBuffer byteBuffer) {
            Log.i("RESPONSE", "****** Request Completed, status code is " + info.getHttpStatusCode() + ", total received bytes is " + info.getReceivedByteCount()+", protocol - "+info.getNegotiatedProtocol());
            byteBuffer.flip();
            Log.i(TAG, "****** onReadCompleted ******" + byteBuffer);
            try {
                mReceiveChannel.write(byteBuffer);
            } catch (IOException e) {
                Log.i(TAG, "IOException during ByteBuffer read. Details: ", e);
            }
            byteBuffer.clear();
            request.read(byteBuffer);
        }

        @Override
        public void onFailed(UrlRequest request, UrlResponseInfo info, CronetException error) {
            Log.i(TAG, "****** onFailed, error is: " + error.getMessage());
            final String url = mUrl;
            final String text = "Failed " + mUrl + " (" + error.getMessage() + ")";
            MainActivity.this.runOnUiThread(new Runnable() {
                public void run() {
                }
            });
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        wvGoogle = (WebView) findViewById(R.id.wvGoogle);
        wvGoogle.getSettings().setJavaScriptEnabled (true);

        enableReadingPermissionForLogging();
        enableWritingPermissionForLogging();
        CronetEngine.Builder builder = new CronetEngine.Builder(this);
        builder
                .addQuicHint("monitor.revsw.net", 443, 443)
                .enableQuic(true)
                .enableHttp2(true);
        engine = builder.build();
        Log.i("VERSION", "API version: "+ApiVersion.getCronetVersion());
        NativeCronetProvider pr = new NativeCronetProvider(this);
        Log.i("VERSION", "NativeCronetProvider"+pr.getVersion());
        JavaCronetProvider jpr = new JavaCronetProvider(this);
        Log.i("VERSION", "JavaCronetProvider"+jpr.getVersion());

        try {
            outputFile = File.createTempFile("nuubit", "log",
                    Environment.getExternalStorageDirectory());
            engine.startNetLogToFile(outputFile.toString(), false);
            Log.i("PERMISSION",engine.getVersionString().toString());

        } catch (IOException e) {
            e.printStackTrace();
        }
        final String appUrl = "https://monitor.revsw.net:443/test-cache.js";
        Button bSend = (Button) findViewById(R.id.bSend);
        bSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startWithURL(appUrl);
            }
        });
        ;
    }
    @Override
    public void onPause(){
        super.onPause();
        engine.stopNetLog();
    }
    private void startWithURL(String url) {
        startWithURL(url, null);
    }

    private void startWithURL(String url, String postData) {
        Log.i(TAG, "Cronet started: " + url);
        mUrl = url;
        String userAgent = System.getProperty("http.agent");
        Log.i("VERSION", userAgent);
        executor = Executors.newSingleThreadExecutor();
        UrlRequest.Callback callback = new SimpleUrlRequestCallback();
        UrlRequest.Builder builder = engine.newUrlRequestBuilder(url, callback, executor)
                .addHeader("Alternate-Protocol","443:quic")
                .addHeader("User-Agent",userAgent);
        UrlRequest req = builder.build();
        req.start();
    }
    private void enableWritingPermissionForLogging() {
        int REQUEST_EXTERNAL_STORAGE = 1;
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        int permission = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }else{
            Log.i("PERMISSION", "Write granted");
        }
    }
    private void enableReadingPermissionForLogging() {
        int REQUEST_EXTERNAL_STORAGE = 1;
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.READ_EXTERNAL_STORAGE
        };

        int permission = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }else{
            Log.i("PERMISSION", "Readed granted");
        }
    }

}
