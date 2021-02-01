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

# per https://medium.com/@kenkyee/android-kotlin-coroutine-best-practices-bc033fed62e7
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# Needed for protobufs
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageV3 { <fields>; }
-keep class com.geeksville.mesh.**{*;}
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }

# Recommended by mapbox to prevent native crashes
-dontwarn com.mapbox.**
-keep class com.mapbox.** { *; }

# for kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.geeksville.mesh.**$$serializer { *; }
-keepclassmembers class com.geeksville.mesh.** {
    *** Companion;
}
-keepclasseswithmembers class com.geeksville.mesh.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Our app is opensource no need to obsfucate
-dontobfuscate
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable