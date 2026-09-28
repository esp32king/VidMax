package com.example.myapplication;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * DiskWala protects its API with a WebAssembly "Appicrypt" attestation header
 * that cannot be reproduced outside their own runtime. So instead of calling the
 * API ourselves, we load the DiskWala page inside a tiny hidden WebView, let
 * their own JavaScript + WASM sign the request and start the player, and sniff
 * the real media URL (.m3u8 / .mp4) off the network. That URL is then handed to
 * yt-dlp for the actual download.
 */
public final class DiskwalaResolver {

    public interface Callback {
        /** mediaUrl is null on failure/timeout. */
        void onResolved(String mediaUrl);
    }

    private static final Pattern MEDIA = Pattern.compile(
            "(?i)https?://[^\\s\"'<>]+\\.(m3u8|mp4|m4s|ts)(\\?[^\\s\"'<>]*)?");
    // DiskWala serves media from these hosts even without an obvious extension.
    private static final Pattern MEDIA_HOST = Pattern.compile(
            "(?i)https?://[^/]*(diskwala|cloudfront|akamaized|b-cdn|bunnycdn|r2\\.dev|amazonaws)[^\\s\"'<>]*");

    public static boolean isDiskwala(String url) {
        return url != null && url.toLowerCase().contains("diskwala.com");
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public static void resolve(final Context ctx, final String pageUrl, final Callback cb) {
        final Handler main = new Handler(Looper.getMainLooper());
        main.post(new Runnable() {
            @Override
            public void run() {
                final Context app = ctx.getApplicationContext();
                final WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
                final WebView web = new WebView(app);
                final AtomicBoolean fired = new AtomicBoolean(false);

                final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        2, 2,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP | Gravity.START;
                lp.x = 0;
                lp.y = 0;

                final Runnable destroy = new Runnable() {
                    @Override
                    public void run() {
                        try { web.stopLoading(); } catch (Throwable ignored) {}
                        try { wm.removeViewImmediate(web); } catch (Throwable ignored) {}
                        try { web.destroy(); } catch (Throwable ignored) {}
                    }
                };

                final Runnable timeout = new Runnable() {
                    @Override
                    public void run() {
                        if (fired.compareAndSet(false, true)) {
                            destroy.run();
                            cb.onResolved(null);
                        }
                    }
                };

                WebSettings s = web.getSettings();
                s.setJavaScriptEnabled(true);
                s.setDomStorageEnabled(true);
                s.setMediaPlaybackRequiresUserGesture(false);
                s.setUserAgentString(
                        "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36");

                web.setWebViewClient(new WebViewClient() {
                    private void consider(String u) {
                        if (u == null || fired.get()) return;
                        boolean isMedia = MEDIA.matcher(u).find();
                        // treat CDN hosts as media only if it also looks like a stream/file
                        if (!isMedia && MEDIA_HOST.matcher(u).find()
                                && (u.contains(".m3u8") || u.contains(".mp4")
                                    || u.contains("playlist") || u.contains("/hls")
                                    || u.contains("master"))) {
                            isMedia = true;
                        }
                        if (isMedia) {
                            final String found = u;
                            main.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (fired.compareAndSet(false, true)) {
                                        main.removeCallbacks(timeout);
                                        destroy.run();
                                        cb.onResolved(found);
                                    }
                                }
                            });
                        }
                    }

                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                        if (request != null && request.getUrl() != null) {
                            consider(request.getUrl().toString());
                        }
                        return super.shouldInterceptRequest(view, request);
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        // nudge the player: click any play button and start any <video>
                        view.evaluateJavascript(
                                "(function(){try{"
                                        + "var v=document.querySelector('video');"
                                        + "if(v){v.muted=true;var p=v.play();if(p&&p.catch)p.catch(function(){});}"
                                        + "var els=document.querySelectorAll('button,[class*=play],[class*=Play],svg,.vjs-big-play-button');"
                                        + "for(var i=0;i<els.length && i<8;i++){try{els[i].click();}catch(e){}}"
                                        + "}catch(e){}})();", null);
                        // try again shortly (SPA renders after load)
                        main.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (fired.get()) return;
                                view.evaluateJavascript(
                                        "(function(){try{var v=document.querySelector('video');"
                                                + "if(v){v.muted=true;var p=v.play();if(p&&p.catch)p.catch(function(){});}"
                                                + "}catch(e){}})();", null);
                            }
                        }, 2500);
                    }
                });

                try {
                    wm.addView(web, lp);
                    web.loadUrl(pageUrl);
                    main.postDelayed(timeout, 30000);
                } catch (Throwable t) {
                    if (fired.compareAndSet(false, true)) {
                        destroy.run();
                        cb.onResolved(null);
                    }
                }
            }
        });
    }

    private DiskwalaResolver() {}
}
