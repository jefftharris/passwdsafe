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

#
# PasswdSafe
#
-keep,includedescriptorclasses class com.jefftharris.passwdsafe.lib.StartupReceiver

#
# logback-android.  From project wiki narrowed to just what is needed for logcat
#

# Issue #229
-keepclassmembers class ch.qos.logback.classic.pattern.* { <init>(); }

-keep public class org.slf4j.impl.** { *; }
-keep public class ch.qos.logback.classic.** { *; }
-keepattributes *Annotation*
-dontwarn ch.qos.logback.core.net.*

#
# Misc
#
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
-dontwarn javax.annotation.Nonnull
-dontwarn javax.annotation.Nullable


#-printconfiguration /tmp/full-r8-config.txt
