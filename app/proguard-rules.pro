# kotlinx.serialization / OkHttp / WorkManager / Room / Glance 均自带 consumer keep 规则，
# 这里只保留项目自身需要的部分。javax.crypto / java.security 是平台类，无需 keep。

-keepattributes RuntimeVisibleAnnotations, AnnotationDefault

# ── kotlinx.serialization：@Serializable 模型的生成序列化器与 Companion.serializer() ──
-keep class com.rzh.valo.data.**$$serializer { *; }
-keepclassmembers class com.rzh.valo.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# ── OkHttp 可选安全依赖（官方建议的 dontwarn） ──
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
