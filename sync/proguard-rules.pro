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
# OneDrive
#
-keep class com.microsoft.identity.common.** { *; }
-keep class com.microsoft.identity.client.PublicClientApplication { *; }
-keep class com.microsoft.identity.client.PublicClientApplicationConfiguration { *; }
-keep class com.microsoft.identity.** { *; }
-keepclassmembers class com.microsoft.graph.extensions.** { *; }
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

##---------------Begin: proguard configuration for MSAL  --------
-keep class com.microsoft.identity.** { *; }
-keep class com.microsoft.device.display.** { *; }

##---------------Begin: proguard configuration for Nimbus  ----------
#-keep class com.nimbusds.** { *; }

##---------------Begin: proguard configuration for Gson  --------
# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-dontwarn sun.misc.**
#-keep class com.google.gson.stream.** { *; }

# Application classes that will be serialized/deserialized over Gson
#-keep class com.google.gson.examples.android.model.** { <fields>; }

# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Prevent R8 from leaving Data object members always null
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Retain generic signatures of TypeToken and its subclasses with R8 version 3.0
# and higher. From https://github.com/google/gson/blob/main/examples/android-proguard-example/proguard.cfg
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

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
