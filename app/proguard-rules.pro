-keepattributes *Annotation*, InnerClasses, EnclosingMethod

# ── kotlinx.serialization ──
-keep,includedescriptorclasses class com.rzh.valo.data.** { *; }
-keepclassmembers class com.rzh.valo.data.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.rzh.valo.data.**$$serializer { *; }
-keepclassmembers class * extends kotlinx.serialization.internal.PluginGeneratedSerialDescriptor {
    <fields>;
}

# ── OkHttp ──
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── JCE / Crypto (AES-CBC in HaojiaoApi) ──
-keep class javax.crypto.** { *; }
-keep class javax.crypto.spec.** { *; }
-keep class java.security.** { *; }

# ── Glance widget ──
-keep class com.rzh.valo.widget.** { *; }

# ── AndroidX WorkManager / Room (反射实例化 WorkDatabase_Impl) ──
-keep class * extends androidx.work.Worker { <init>(...); }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class * extends androidx.work.CoroutineWorker { <init>(...); }
-keep class * extends androidx.room.RoomDatabase { <init>(...); }
-keep class * extends androidx.room.RoomDatabase$Callback { <init>(...); }
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(...); }
