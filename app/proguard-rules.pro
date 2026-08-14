# Add project specific ProGuard rules here.
# Keep OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep data models (used in JSON parsing)
-keep class com.example.agenticos.model.** { *; }
