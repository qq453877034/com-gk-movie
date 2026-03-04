plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // 启用 Compose 编译器插件
    alias(libs.plugins.kotlin.compose)
    // 启用 JSON 序列化插件
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.gk.movie"
    compileSdk = 36 

    defaultConfig {
        applicationId = "com.gk.movie"
        minSdk = 26 
        targetSdk = 36
        versionCode = 100
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildFeatures {
        // 开启 Compose
        compose = true

    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("17"))
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // --- Compose 核心 ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // --- 必须添加的扩展图标库 (使用正确版本) ---
    implementation(libs.androidx.material.icons.extended)
    
    // --- 窗口大小感知库 (判断是手机还是平板) ---
    implementation(libs.material3.window.size)

    // --- Pad 自适应开发核心 (Adaptive) ---
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation)

    // --- 工具库 (OkHttp, Glide, Jsoup, JSON) ---
    implementation(libs.okhttp)
    implementation(libs.glide.compose)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)

    // 9. 媒体播放 (Media3)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.decoder)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.media3.ui.compose.material3)
    // Media3 FFmpeg扩展库 
    implementation(libs.org.jellyfin.media3.ffmpeg)  
    
    // --- 调试工具 ---
    debugImplementation(libs.androidx.ui.tooling)
}
