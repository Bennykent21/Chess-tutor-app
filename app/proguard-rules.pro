# ProGuard / R8 Rules for Chess Tutor

# Preserve line numbers and source file names for diagnostics
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep native methods and JNI bindings
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep class com.chesstutor.app.data.local.** { *; }

# Moshi DTOs and reflection/codegen adapters
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
    @com.squareup.moshi.JsonClass *;
}
-keep class com.chesstutor.app.data.network.** { *; }
-dontwarn sun.misc.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
