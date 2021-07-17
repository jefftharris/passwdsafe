# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/jharris/opt/android-sdk-linux/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-dontobfuscate

-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes *Annotation*

-keep,includedescriptorclasses class com.jefftharris.passwdsafe.lib.ProviderType
-keep,includedescriptorclasses class com.jefftharris.passwdsafe.lib.StartupReceiver
-keep,includedescriptorclasses class com.jefftharris.passwdsafe.sync.PasswdSafeProvider

# Serializable classes
-keepnames class * implements java.io.Serializable

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Android
-keepclassmembers class android.app.Notification$Action {
    android.app.PendingIntent actionIntent;
    int icon;
    java.lang.CharSequence title;
}

# Apache http
-keep class org.apache.commons.httpclient.auth.BasicScheme
-keep class org.apache.commons.httpclient.auth.DigestScheme
-keep class org.apache.commons.httpclient.auth.NTLMScheme
-keep class org.apache.commons.httpclient.cookie.**

# Box library
-keepclasseswithmembers,includedescriptorclasses class com.box.boxandroidlibv2.dao.** { *; }

# OkHttp
-dontwarn okio.ForwardingSink

# OneDrive
-keep class com.microsoft.graph.extensions.Hashes
-keep class com.microsoft.identity.common.** { *; }
-keepclassmembers class com.microsoft.graph.extensions.** { *; }

# Retrofit library
-keep,includedescriptorclasses class retrofit.** { *; }
-keepclasseswithmembers class * {
    @retrofit.http.* <methods>;
}

# Slf4j
-dontwarn org.slf4j.**

# Dropbox unused dependencies
-dontwarn okhttp3.**
-dontwarn com.squareup.okhttp.**
-dontwarn javax.servlet.**
