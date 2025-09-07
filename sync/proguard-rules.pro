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

#
# Serializable classes
#
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

#
# Android
#
-keepclassmembers class android.app.Notification$Action {
    android.app.PendingIntent actionIntent;
    int icon;
    java.lang.CharSequence title;
}

#
# Apache http
#
-keep class org.apache.commons.httpclient.cookie.**

#
# Google Drive
#
-keepclassmembers class * {
  @com.google.api.client.util.Key <fields>;
}

-keep class com.google.api.services.drive.model.** { *; }
-keep class com.google.api.client.googleapis.json.** { *; }
#-keep class com.google.api.services.drive.model.** { *; }
#-keep class com.google.api.client.googleapis.json.** { *; }

#
# Box library
#
-keepclasseswithmembers,includedescriptorclasses class com.box.boxandroidlibv2.dao.** { *; }
-keep class com.box.androidsdk.content.models.** { *; }

#
# MSAL
#
-dontwarn net.jcip.annotations.*

#
# OneDrive
#
# OneDrive MSAL library has its own proguard rules file.  MS graph needs
# specific rules.

# For the PageIterator, need the collection response classes
-keep class com.microsoft.graph.models.DriveItemCollectionResponse { *; }


#
# Retrofit library
#
-keep,includedescriptorclasses class retrofit.** { *; }
-keepclasseswithmembers class * {
    @retrofit.http.* <methods>;
}

#
# Slf4j
#
-dontwarn org.slf4j.**

#
# Dropbox unused dependencies
#
-dontwarn okhttp3.**
-dontwarn com.squareup.okhttp.**
-dontwarn javax.servlet.**

#
# okhttp
#
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

#
# okio
#
-keep class sun.misc.**
-dontwarn java.nio.file.*
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn okio.**

#
# Extras required by build
#
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.crypto.tink.subtle.Ed25519Sign$KeyPair
-dontwarn com.google.crypto.tink.subtle.Ed25519Sign
-dontwarn com.google.crypto.tink.subtle.Ed25519Verify
-dontwarn com.google.crypto.tink.subtle.X25519
-dontwarn com.google.crypto.tink.subtle.XChaCha20Poly1305
-dontwarn com.microsoft.device.display.DisplayMask
-dontwarn edu.umd.cs.findbugs.annotations.NonNull
-dontwarn edu.umd.cs.findbugs.annotations.Nullable
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
-dontwarn reactor.blockhound.integration.BlockHoundIntegration

