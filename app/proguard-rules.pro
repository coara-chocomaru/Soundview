# Basic Android ProGuard configuration

# Keep all Android classes and interfaces
-keep class android.** { *; }
-keep interface android.** { *; }

# Prevent warnings for standard Java libraries
-dontwarn java.lang.**

# Prevent mixed case class names
-dontusemixedcaseclassnames

# Keep MainActivity class and its main method
-keep class com.coara.mp3view.MainActivity {
    public static void main(java.lang.String[]);
}

# Keep annotations
-keepattributes *Annotation*

# Remove log statements
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Adapt resource file contents and names
-adaptresourcefilecontents **.xml
-adaptresourcefilenames **.png

# Use a dictionary for class obfuscation
-classobfuscationdictionary obfuscation-dictionary.txt

# Rename source file attribute
-renamesourcefileattribute SourceFile

# Keep specific attributes
-keepattributes Exceptions, InnerClasses, Signature, Deprecated, EnclosingMethod

# Optimization passes
-optimizationpasses 3

# Aggressively merge interfaces
-mergeinterfacesaggressively

# Adapt class strings
-adaptclassstrings

# Repackage classes into the root package
-repackageclasses ''

# Keep WebView JavaScript interfaces 
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebViewClient methods
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String);
    public void *(android.webkit.WebView, android.webkit.WebResourceRequest);
    public boolean *(android.webkit.WebView, android.webkit.WebResourceRequest);
}

# Keep EditText's TextWatcher
-keepclassmembers class * {
    void addTextChangedListener(android.text.TextWatcher);
}

# Keep methods for permissions and storage operations
-keepclassmembers class * {
    public void requestPermissions(androidx.core.app.ActivityCompat, java.lang.String[], int);
    public void onRequestPermissionsResult(int, java.lang.String[], int[]);
}

# Keep inner classes
-keep class **$$Lambda$* { *; }

# Keep Parcelable creator
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep getter and setter methods
-keepclassmembers class * {
    public void set*(...);
    public void get*(...);
}

# Allow access modification
-allowaccessmodification

# Don't preverify
-dontpreverify
