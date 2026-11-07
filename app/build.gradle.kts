plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.soyorinhhh.minicalendar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.soyorinhhh.minicalendar"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 发布版也用 debug 签名，这样 GitHub Actions 出来的 release APK 可以直接安装。
            // 真上架商店时请换成自己的 keystore。
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
