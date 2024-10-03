# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Needed for protobufs
-keep class com.google.protobuf.** { *; }
-keep class com.geeksville.mesh.** { *; }

# eclipse.paho.client
-keep class org.eclipse.paho.client.mqttv3.logging.JSR47Logger { *; }

# ormlite
-dontwarn com.j256.ormlite.**

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ?
-dontwarn java.awt.image.**
-dontwarn java.lang.reflect.**
-dontwarn com.google.errorprone.annotations.**

# Our app is opensource no need to obsfucate
-dontobfuscate
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable
