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
-keep,includedescriptorclasses class android.support.design.widget.NavigationView$OnNavigationItemSelectedListener
-keep,includedescriptorclasses class android.support.design.widget.Snackbar$SnackbarLayout$OnAttachStateChangeListener
-keep,includedescriptorclasses class android.support.design.widget.Snackbar$SnackbarLayout$OnLayoutChangeListener
-keep,includedescriptorclasses class android.support.design.widget.TabLayout$OnTabSelectedListener
-keep,includedescriptorclasses class android.support.v4.view.ActionProvider
-keep,includedescriptorclasses class android.support.v4.view.PagerAdapter
-keep,includedescriptorclasses class android.support.v4.view.ViewPager
-keep,includedescriptorclasses class android.support.v4.view.ViewPager$OnAdapterChangeListener
-keep,includedescriptorclasses class android.support.v4.view.ViewPager$OnPageChangeListener
-keep,includedescriptorclasses class android.support.v4.view.WindowInsetsCompat
-keep,includedescriptorclasses class android.support.v4.widget.CursorAdapter
-keep,includedescriptorclasses class android.support.v4.widget.DrawerLayout$DrawerListener
-keep,includedescriptorclasses class android.support.v4.widget.NestedScrollView$OnScrollChangeListener
-keep,includedescriptorclasses class android.support.v4.widget.SlidingPaneLayout$PanelSlideListener
-keep,includedescriptorclasses class android.support.v4.widget.SwipeRefreshLayout$OnRefreshListener
-keep,includedescriptorclasses class android.support.v7.view.menu.ActionMenuItemView$PopupCallback
-keep,includedescriptorclasses class android.support.v7.view.menu.MenuBuilder$ItemInvoker
-keep,includedescriptorclasses class android.support.v7.widget.ActionBarOverlayLayout$ActionBarVisibilityCallback
-keep,includedescriptorclasses class android.support.v7.widget.ActionMenuPresenter
-keep,includedescriptorclasses class android.support.v7.widget.ActionMenuView$OnMenuItemClickListener
-keep,includedescriptorclasses class android.support.v7.widget.ActivityChooserModel
-keep,includedescriptorclasses class android.support.v7.widget.ContentFrameLayout$OnAttachListener
-keep,includedescriptorclasses class android.support.v7.widget.FitWindowsViewGroup$OnFitSystemWindowsListener
-keep,includedescriptorclasses class android.support.v7.widget.RecyclerView$Adapter
-keep,includedescriptorclasses class android.support.v7.widget.RecyclerView$ChildDrawingOrderCallback
-keep,includedescriptorclasses class android.support.v7.widget.RecyclerView$ItemAnimator
-keep,includedescriptorclasses class android.support.v7.widget.RecyclerView$LayoutManager
-keep,includedescriptorclasses class android.support.v7.widget.RecyclerView$OnScrollListener
-keep,includedescriptorclasses class android.support.v7.widget.RecyclerView$RecycledViewPool
-keep,includedescriptorclasses class android.support.v7.widget.RecyclerView$RecyclerListener
-keep,includedescriptorclasses class android.support.v7.widget.RecyclerView$ViewCacheExtension
-keep,includedescriptorclasses class android.support.v7.widget.RecyclerViewAccessibilityDelegate
-keep,includedescriptorclasses class android.support.v7.widget.ScrollingTabContainerView
-keep,includedescriptorclasses class android.support.v7.widget.SearchView
-keep,includedescriptorclasses class android.support.v7.widget.SearchView$OnCloseListener
-keep,includedescriptorclasses class android.support.v7.widget.SearchView$OnQueryTextListener
-keep,includedescriptorclasses class android.support.v7.widget.SearchView$OnSuggestionListener
-keep,includedescriptorclasses class android.support.v7.widget.Toolbar$OnMenuItemClickListener
-keep,includedescriptorclasses class android.support.v7.widget.ViewStubCompat$OnInflateListener

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
