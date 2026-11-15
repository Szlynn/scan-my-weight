# Preserve line numbers for crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# org.json (used for webhook payload construction)
-keep class org.json.** { *; }

# ML Kit Text Recognition
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Google Tasks (ML Kit dependency)
-keep class com.google.android.gms.tasks.** { *; }

# DataStore / Protobuf
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
