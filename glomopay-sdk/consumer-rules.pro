# WebView invokes these annotated methods by name, so merchant R8 builds must
# preserve the JavaScript-facing members without keeping unrelated SDK code.
-keepclassmembers class com.glomopay.sdk.bridge.GlomoPayJavaScriptBridge {
    @android.webkit.JavascriptInterface <methods>;
}
