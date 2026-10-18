# ProGuard / R8 rules for Chess Tutor.
#
# R8 (minify + shrink resources) is ON for release builds. Compose, Room, OkHttp, Moshi and
# coroutines ship their own consumer rules, so only app-specific needs live here.

# Keep line numbers and file names so release stack traces can be decoded with mapping.txt
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep native methods and JNI bindings
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Room database, entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep class com.chesstutor.app.data.local.** { *; }

# Moshi: DTOs annotated for codegen, plus the reflection fallback (KotlinJsonAdapterFactory)
# which reads Kotlin metadata at runtime.
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
-keep class kotlin.Metadata { *; }
-keep class com.chesstutor.app.data.network.** { *; }
-dontwarn sun.misc.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# OkHttp contains optional TLS implementations for non-Android JVMs. These providers are
# not bundled by this app; Android uses its platform TLS implementation.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
