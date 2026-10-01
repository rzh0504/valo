import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 本地签名配置（keystore.properties 不入库，首次构建前用 keytool 生成 jks）
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.rzh.valo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rzh.valo"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        // CI 发版时由 tag 覆盖（-PvaloVersionName=x.y.z），本地构建保持默认
        versionName = findProperty("valoVersionName")?.toString() ?: "1.0.6"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 与 release 区分包名，允许两版共存（签名各用各的：debug key / 本地 jks）
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    // 桌面小组件（Android 专属）：Glance + WorkManager，配色与 shared 主题一致
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.material3:material3:1.5.0-alpha27")
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.10.2")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
