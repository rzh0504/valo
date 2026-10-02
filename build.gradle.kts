plugins {
    id("com.android.application") version "9.4.1" apply false
    id("com.android.kotlin.multiplatform.library") version "9.4.1" apply false
    id("com.android.test") version "9.4.1" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
    id("org.jetbrains.compose") version "1.12.1" apply false
    // baseline profile：冷启动/首屏热路径随 APK 预编译（AOT），消除每次发版更新后的 JIT 编译窗口期卡顿
    id("androidx.baselineprofile") version "1.5.0" apply false
}
