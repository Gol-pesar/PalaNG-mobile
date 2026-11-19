# Xray-core & Native JNI / Gomobile keep rules
-keep class com.example.xrayclient.engine.** { *; }
-keepclassmembers class com.example.xrayclient.engine.** { *; }

# Keep official upstream gomobile / libXray / libv2ray bindings when integrated
-keep class go.** { *; }
-keep class libXray.** { *; }
-keep class libv2ray.** { *; }
-keep class com.github.xtls.libxray.** { *; }
-keep class hev.htproxy.** { *; }

# Keep Kotlinx Serialization routes & models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.example.xrayclient.**$$serializer { *; }
-keepclassmembers class com.example.xrayclient.** { *** Companion; }
-keepclasseswithmembers class com.example.xrayclient.** { kotlinx.serialization.KSerializer serializer(...); }
