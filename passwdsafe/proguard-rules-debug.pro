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

-keep,includedescriptorclasses public class android.view.** { *; }
-keep,includedescriptorclasses public class androidx.annotation.** { *; }
-keep,includedescriptorclasses public class androidx.drawerlayout.** { *; }
#noinspection ExpensiveKeepRuleInspection
-keep,includedescriptorclasses public class androidx.recyclerview.** { *; }
-keep,includedescriptorclasses public class androidx.test.** { *; }
#noinspection ShrinkerUnresolvedReference
-keep,includedescriptorclasses public class androidx.tracing.Trace { *; }
-keep,includedescriptorclasses public class kotlin.collections.** { *; }
-keep,includedescriptorclasses public class kotlin.reflect.** { *; }
#noinspection ExpensiveKeepRuleInspection
-keep,includedescriptorclasses public class kotlin.jvm.** { *; }
#noinspection ExpensiveKeepRuleInspection
-keep,includedescriptorclasses public class com.google.android.material.** { *; }
-dontwarn androidx.window.**

-keepclassmembers class **.R$* {
    public static <fields>;
}


#
# Needed for tests which use Kotlin
#
-keep public class kotlin.LazyKt { *; }

#
# PasswdSafe items needed for testing
#
-keepclassmembers public class org.pwsafe.lib.file.PwsFile {
*** isModified(...);
*** setStorage(...);
}

-keepnames public class com.jefftharris.passwdsafe.file.PasswdPolicy { *; }
-keepnames public class com.jefftharris.passwdsafe.file.PasswdPolicy$RecordPolicyStrs { *; }

#
# Don't optimize in debug mode.  Allow shrinking to do the main
# ProGuard update which are the cause of runtime problems.  Optimizing can
# remove inlined methods from the code which are needed for tests.
#
-dontoptimize

#
# Disable obfuscation if needed to debug problems.  The setting, however, can
# mask real problems.
#
#-dontobfuscate
