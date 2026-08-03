# Firestore models need to be kept for reflection
-keepclassmembers class com.example.gasml.model.** { *; }
-keep class com.example.gasml.model.** { *; }

# Keep Compose internal annotations
-keepattributes RuntimeVisible*Annotations*
-dontwarn androidx.compose.**
