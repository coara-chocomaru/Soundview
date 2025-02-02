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

# Keep necessary androidx and support library classes
-keep class android.support.v4.media.session.** { *; }
-keep class androidx.media.session.** { *; }

# Keep annotations
-keepattributes *Annotation*

# Remove log statements to optimize size
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Adapt resource file contents and names for obfuscation
-adaptresourcefilecontents **.xml
-adaptresourcefilenames **.png

# Use a dictionary for class obfuscation
-classobfuscationdictionary obfuscation-dictionary.txt

# Rename source file attribute to avoid leaking file names
-renamesourcefileattribute SourceFile

# Keep specific attributes for debugging and other purposes
-keepattributes Exceptions, InnerClasses, Signature, Deprecated, EnclosingMethod

# Optimization passes (to speed up the optimization process)
-optimizationpasses 3

# Aggressively merge interfaces for better obfuscation
-mergeinterfacesaggressively

# Adapt class strings for obfuscation
-adaptclassstrings

# Repackage classes into the root package to minimize package names
-repackageclasses ''

# Keep WebView JavaScript interfaces to avoid stripping
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebViewClient methods to avoid stripping
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String);
    public void *(android.webkit.WebView, android.webkit.WebResourceRequest);
    public boolean *(android.webkit.WebView, android.webkit.WebResourceRequest);
}

# Keep TextWatcher methods for EditText to avoid stripping
-keepclassmembers class * {
    void addTextChangedListener(android.text.TextWatcher);
}

# Keep methods for permissions and storage operations to avoid issues
-keepclassmembers class * {
    public void requestPermissions(androidx.core.app.ActivityCompat, java.lang.String[], int);
    public void onRequestPermissionsResult(int, java.lang.String[], int[]);
}

# Keep inner classes (lambdas and other inner classes)
-keep class **$$Lambda$* { *; }

# Keep Parcelable creator methods for Parcelable classes
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep native methods to prevent stripping native code
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep getter and setter methods to avoid stripping them
-keepclassmembers class * {
    public void set*(...);
    public void get*(...);
}

# Allow access modification for libraries
-allowaccessmodification

# Don't preverify classes (for optimization purposes)
-dontpreverify
