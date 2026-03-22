-dontwarn android.os.Parcel**
-dontwarn android.os.Parcelable**
-dontwarn com.squareup.wire.AndroidMessage**
-dontwarn io.ktor.**

# Room KMP: preserve generated database constructor (required for R8/ProGuard)
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Suppress ProGuard notes about duplicate resource files (common in Compose Desktop)
-dontnote **

# Suppress specific reflection warnings that are safe to ignore
-dontwarn java.lang.reflect.**
-dontwarn sun.misc.Unsafe