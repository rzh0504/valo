import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

val miuixVersion = "0.9.4"
val ktorVersion = "3.2.2"
val coilVersion = "3.2.0"
kotlin {
    android {
        namespace = "com.rzh.valo.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest { }
        compilerOptions {
            // androidx 新库以 JVM 21 字节码发布，消费方需同级别
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    // iOS framework 供 iosApp Xcode 工程消费（真机 + 模拟器）
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // UI：miuix（HyperOS 风格 Compose Multiplatform 组件库）
            implementation("top.yukonga.miuix.kmp:miuix-ui:$miuixVersion")
            implementation("top.yukonga.miuix.kmp:miuix-preference:$miuixVersion")
            implementation("top.yukonga.miuix.kmp:miuix-nav:$miuixVersion")
            implementation("top.yukonga.miuix.kmp:miuix-icons:$miuixVersion")
            implementation("top.yukonga.miuix.kmp:miuix-squircle:$miuixVersion")

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.animation)
            // JetBrains 发布的多平台图标（androidx 坐标无 iOS 变体）
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

            // 生命周期（JetBrains KMP 移植，Android 包名保持 androidx.lifecycle.*）
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

            // 数据层
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            implementation("io.ktor:ktor-client-core:$ktorVersion")
            implementation("io.coil-kt.coil3:coil-compose:$coilVersion")
            implementation("io.coil-kt.coil3:coil-network-ktor3:$coilVersion")
            implementation("com.squareup.okio:okio:3.11.0")
            implementation("androidx.datastore:datastore-preferences-core:1.2.1")
        }

        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
        }

        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:$ktorVersion")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
    }
}
