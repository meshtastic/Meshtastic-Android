-dontwarn android.os.Parcel**
-dontwarn android.os.Parcelable**
-dontwarn com.squareup.wire.AndroidMessage**
-dontwarn io.ktor.**

# Suppress ProGuard notes about duplicate resource files (common in Compose Desktop)
-dontnote **

# Suppress specific reflection warnings that are safe to ignore
-dontwarn java.lang.reflect.**
-dontwarn sun.misc.Unsafe