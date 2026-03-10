# ─── Lumera ProGuard / R8 Rules ───

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature
-keepattributes *Annotation*

# ─── App code ───
# Keep all app classes to prevent R8 class merging/obfuscation issues
# with Room, Gson, and Hilt. R8 still shrinks unused code and
# obfuscates third-party libraries.
-keep class com.lumera.app.** { *; }

# ─── Gson ───
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ─── Room ───
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class *_Impl { *; }

# ─── Hilt / Dagger ───
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }


# ─── NanoHTTPD (remote input hub) ───
-keep class fi.iki.elonen.** { *; }

# ─── ZXing ───
-keep class com.google.zxing.** { *; }

# ─── AndroidX Security (EncryptedSharedPreferences) ───
-keep class androidx.security.crypto.** { *; }

# ─── Compose ───
-dontwarn androidx.compose.**

# ─── Media3 / ExoPlayer ───
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ─── Retrofit ───
# Keep generic signature and annotations for Retrofit + Gson
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
# R8 full mode strips generic signatures from return types if not kept.
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Kotlin coroutines continuation (needed by Retrofit suspend functions)
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Kotlin Metadata (needed by Retrofit to understand suspend functions)
-keep class kotlin.Metadata { *; }

# ─── OkHttp ───
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ─── ACRA (Crash Reporting) ───
-keep class org.acra.** { *; }
-dontwarn org.acra.**

# ─── General ───
-dontwarn javax.annotation.**
-dontwarn kotlin.reflect.jvm.internal.**
