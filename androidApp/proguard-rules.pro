# WebRTC is reached from native code through JNI.
-keep class org.webrtc.** { *; }
-keep class com.shepeliev.webrtckmp.** { *; }

# Firebase Realtime Database reads and writes by reflection.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep class com.google.firebase.** { *; }

# kotlinx.serialization (signalling envelopes, navigation routes)
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer { *; }

# ML Kit barcode scanning
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

-dontwarn org.slf4j.**
