package com.flutter_webview_plugin;

import android.content.Intent;
import android.net.Uri;
import android.annotation.TargetApi;
import android.app.Activity;

import android.os.*;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import java.util.HashMap;
import java.util.Map;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

import static android.app.Activity.RESULT_OK;

import android.webkit.JavascriptInterface;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by lejard_h on 20/12/2017.
 */

class WebviewManager {

    private ValueCallback<Uri> mUploadMessage;
    private ValueCallback<Uri[]> mUploadMessageArray;
    private final static int FILECHOOSER_RESULTCODE = 1;
    private static final String CHANNEL_NAME = "flutter_webview_plugin";

    @TargetApi(7)
    class ResultHandler {
        public boolean handleResult(int requestCode, int resultCode, Intent intent) {
            boolean handled = false;
            if(Build.VERSION.SDK_INT >= 21){
                if(requestCode == FILECHOOSER_RESULTCODE){
                    Uri[] results = null;
                    if(resultCode == Activity.RESULT_OK && intent != null){
                        String dataString = intent.getDataString();
                        if(dataString != null){
                            results = new Uri[]{ Uri.parse(dataString) };
                        }
                    }
                    if(mUploadMessageArray != null){
                        mUploadMessageArray.onReceiveValue(results);
                        mUploadMessageArray = null;
                    }
                    handled = true;
                }
            }else {
                if (requestCode == FILECHOOSER_RESULTCODE) {
                	Uri result = null;
                    if (resultCode == RESULT_OK && intent != null) {
                        result = intent.getData();
                    }
                    if (mUploadMessage != null) {
                        mUploadMessage.onReceiveValue(result);
                        mUploadMessage = null;
                    }
                    handled = true;
                }
            }
            return handled;
        }
    }

    boolean closed = false;
    WebView webView;
    Activity activity;
    BrowserClient webViewClient;
    ResultHandler resultHandler;

    WebviewManager(final Activity activity) {
        this.webView = new ObservableWebView(activity);
        this.activity = activity;
        this.resultHandler = new ResultHandler();
        webViewClient = new BrowserClient();
        webView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                    case KeyEvent.KEYCODE_BACK:
                        if (webView.canGoBack()) {
                            webView.goBack();
                        } else {
                            close();
                        }
                        return true;
                    }
                }

                return false;
            }
        });

        ((ObservableWebView) webView).setOnScrollChangedCallback(new ObservableWebView.OnScrollChangedCallback() {
            public void onScroll(int x, int y, int oldx, int oldy) {
                Map<String, Object> yDirection = new HashMap<>();
                yDirection.put("yDirection", (double) y);
                FlutterWebviewPlugin.channel.invokeMethod("onScrollYChanged", yDirection);
                Map<String, Object> xDirection = new HashMap<>();
                xDirection.put("xDirection", (double) x);
                FlutterWebviewPlugin.channel.invokeMethod("onScrollXChanged", xDirection);
            }
        });

        webView.setWebViewClient(webViewClient);
        webView.setWebChromeClient(new WebChromeClient()
        {
            // For opening external browser window
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                          Message resultMsg) {
                WebView newWebView = new WebView(view.getContext());
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newWebView);
                resultMsg.sendToTarget();
                return true;
            }
            //The undocumented magic method override
            //Eclipse will swear at you if you try to put @Override here
            // For Android 3.0+
            public void openFileChooser(ValueCallback<Uri> uploadMsg) {

                mUploadMessage = uploadMsg;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                activity.startActivityForResult(Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);

            }

            // For Android 3.0+
            public void openFileChooser(ValueCallback uploadMsg, String acceptType) {
                mUploadMessage = uploadMsg;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                activity.startActivityForResult(Intent.createChooser(i, "File Browser"), FILECHOOSER_RESULTCODE);
            }

            // For Android 4.1
            public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
                mUploadMessage = uploadMsg;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                activity.startActivityForResult(Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);

            }

            // For Android 5.0+
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (mUploadMessageArray != null) {
                    mUploadMessageArray.onReceiveValue(null);
                }
                mUploadMessageArray = filePathCallback;

                Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
                contentSelectionIntent.setType("*/*");
                Intent[] intentArray;
                intentArray = new Intent[0];

                Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
                chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
                chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
                activity.startActivityForResult(chooserIntent, FILECHOOSER_RESULTCODE);
                return true;
            }
        });
    }

    private void clearCookies() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().removeAllCookies(new ValueCallback<Boolean>() {
                @Override
                public void onReceiveValue(Boolean aBoolean) {

                }
            });
        } else {
            CookieManager.getInstance().removeAllCookie();
        }
    }

    private void clearCache() {
        webView.clearCache(true);
        webView.clearFormData();
    }

    void openUrl(
            boolean withJavascript,
            boolean clearCache,
            boolean hidden,
            boolean clearCookies,
            String userAgent,
            String url,
            Map<String, String> headers,
            boolean withZoom,
            boolean withLocalStorage,
            boolean scrollBar,
            boolean supportMultipleWindows,
            boolean appCacheEnabled,
            boolean allowFileURLs,
            boolean enableMessaging
            boolean useWideViewPort,
            String invalidUrlRegex
    ) {
        webView.getSettings().setJavaScriptEnabled(withJavascript);
        webView.getSettings().setBuiltInZoomControls(withZoom);
        webView.getSettings().setSupportZoom(withZoom);
        webView.getSettings().setDomStorageEnabled(withLocalStorage);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(supportMultipleWindows);

        webView.getSettings().setSupportMultipleWindows(supportMultipleWindows);

        webView.getSettings().setAppCacheEnabled(appCacheEnabled);

        webView.getSettings().setAllowFileAccessFromFileURLs(allowFileURLs);
        webView.getSettings().setAllowUniversalAccessFromFileURLs(allowFileURLs);

        webView.getSettings().setUseWideViewPort(useWideViewPort);

        webViewClient.updateInvalidUrlRegex(invalidUrlRegex);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        if (clearCache) {
            clearCache();
        }

        if (hidden) {
            webView.setVisibility(View.INVISIBLE);
        }

        if (clearCookies) {
            clearCookies();
        }

        if (userAgent != null) {
            webView.getSettings().setUserAgentString(userAgent);
        }

        if(!scrollBar){

            webView.setVerticalScrollBarEnabled(false);
        }

        if (headers != null) {
            webView.loadUrl(url, headers);
        } else {
            webView.loadUrl(url);
        }

        if (enableMessaging) {
            enableMessaging();
        }

    }

    void reloadUrl(String url) {
        webView.loadUrl(url);
    }

    void close(MethodCall call, MethodChannel.Result result) {
        if (webView != null) {
            ViewGroup vg = (ViewGroup) (webView.getParent());
            vg.removeView(webView);
        }
        webView = null;
        if (result != null) {
            result.success(null);
        }

        closed = true;
        FlutterWebviewPlugin.channel.invokeMethod("onDestroy", null);
    }

    void close() {
        close(null, null);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    void eval(MethodCall call, final MethodChannel.Result result) {
        String code = call.argument("code");

        webView.evaluateJavascript(code, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                result.success(value);
            }
        });
    }
    /**
    * Reloads the Webview.
    */

    void reload(MethodCall call, MethodChannel.Result result) {
        if (webView != null) {
            webView.reload();
        }
    }
    /**
    * Navigates back on the Webview.
    */

    void back(MethodCall call, MethodChannel.Result result) {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        }
    }
    /**
    * Navigates forward on the Webview.
    */

    void forward(MethodCall call, MethodChannel.Result result) {
        if (webView != null && webView.canGoForward()) {
            webView.goForward();
        }
    }

    void resize(FrameLayout.LayoutParams params) {
        webView.setLayoutParams(params);
    }
    /**
    * Checks if going back on the Webview is possible.
    */
    boolean canGoBack() {
        return webView.canGoBack();
    }
    /**
    * Checks if going forward on the Webview is possible.
    */

    boolean canGoForward() {
        return webView.canGoForward();
    }

    void hide(MethodCall call, MethodChannel.Result result) {
        if (webView != null) {
            webView.setVisibility(View.INVISIBLE);
        }
    }

    void show(MethodCall call, MethodChannel.Result result) {
        if (webView != null) {
            webView.setVisibility(View.VISIBLE);
        }
    }

    void stopLoading(MethodCall call, MethodChannel.Result result) {
        if (webView != null) {
            webView.stopLoading();
        }
    }

    protected static class JsObject {
        WebviewManager webviewManager;

        JsObject(WebviewManager _webviewManager) {
            webviewManager = _webviewManager;
        }

        @JavascriptInterface
        public void postMessage(String message) {
            webviewManager.onMessage(message);
        }
    }

    public void onMessage(String message) {
        Map<String, String> _event = new HashMap<>();
        _event.put("data", (String) message);
        FlutterWebviewPlugin.channel.invokeMethod("onWebviewMessage", _event);
    }

    public void postMessage(MethodCall call, final MethodChannel.Result result) {
        String message = call.argument("data");
        try {
            JSONObject eventInitDict = new JSONObject();
            eventInitDict.put("data", message);
            String script = "(function () {" + "var event;" + "var data = " + eventInitDict.toString() + ";" + "try {"
                    + "event = new MessageEvent('message', data);" + "} catch (e) {"
                    + "event = document.createEvent('MessageEvent');"
                    + "event.initMessageEvent('message', true, true, data.data, data.origin, data.lastEventId, data.source);"
                    + "}" + "document.dispatchEvent(event);" + "})();";
            webView.evaluateJavascript(script, null);

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    protected void enableMessaging() {
        webView.addJavascriptInterface(new JsObject(this), CHANNEL_NAME);
    }

    protected void linkBridge() {
        String script = "(" + "window.originalPostMessage = window.postMessage,"
                + "window.postMessage = function(data) {" + CHANNEL_NAME + ".postMessage(String(data));" + "}" + ")";

        webView.evaluateJavascript(script, null);
    }
}
