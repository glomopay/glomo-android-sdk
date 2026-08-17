# Preserve source positions so explicitly captured SDK failures can be
# deobfuscated with the final merchant application mapping file.
-keepattributes SourceFile,LineNumberTable

# WebView invokes these annotated methods by name. Keep only the bridge members
# and their runtime annotations; the rest of the SDK remains shrinkable.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault
-keepclassmembers,allowoptimization class com.glomopay.sdk.android.bridge.GlomoPayJavaScriptBridge {
    @android.webkit.JavascriptInterface <methods>;
}
