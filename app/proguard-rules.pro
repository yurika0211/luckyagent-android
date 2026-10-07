# LuckyAgent Android — keep rules placeholder
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class com.luckyagent.android.data.api.** { *; }
-keep class com.luckyagent.android.data.settings.PairingQr { *; }
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-keep class androidx.camera.** { *; }
