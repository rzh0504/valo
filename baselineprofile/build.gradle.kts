plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.rzh.valo.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        // benchmark 1.4+ 移除了 MacrobenchmarkRunner，统一使用标准 AndroidJUnitRunner
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // benchmark 构建类型：行为对齐 release（不可调试），debug 签名便于本地/CI 生成 profile
    buildTypes {
        create("benchmark") {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":androidApp"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
}

/** 优先使用已连接设备；留空 managedDevices 即不下载 GMD 系统镜像 */
baselineProfile {
    useConnectedDevices = true
}
