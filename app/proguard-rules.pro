# Open Note ProGuard Rules

-keepattributes Signature
-keepattributes *Annotation*

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Gson
-keep class com.open.note.data.remote.dto.** { *; }

# Room
-keep class com.open.note.data.local.entity.** { *; }
