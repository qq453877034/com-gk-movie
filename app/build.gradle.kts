import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // 启用 Compose 编译器插件
    alias(libs.plugins.kotlin.compose)
    // 启用 JSON 序列化插件
    alias(libs.plugins.kotlin.serialization)
}


// --- 1. 定义变量 ---
val keystoreFile = file("release.keystore")
val keyPwd = "android"
val keyAliasName = "key0" // 变量名重命名，避免冲突

// --- 2. 自动生成 Keystore (使用标准 Java ProcessBuilder) ---
if (!keystoreFile.exists()) {
    println("Keystore file not found. Generating: ${keystoreFile.absolutePath}")
    
    val cmd = listOf(
        "keytool", "-genkey",
        "-v",
        "-keystore", keystoreFile.absolutePath,
        "-alias", keyAliasName,
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-storepass", keyPwd,
        "-keypass", keyPwd,
        "-dname", "CN=Android, OU=Android, O=Android, L=Unknown, ST=Unknown, C=Unknown"
    )
    
    try {
        val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
            
        val exitCode = process.waitFor()
        
        if (exitCode != 0) {
            val output = process.inputStream.bufferedReader().use { it.readText() }
            throw RuntimeException("Failed to generate keystore. Exit code: $exitCode\nOutput: $output")
        } else {
            println("Keystore generated successfully.")
        }
    } catch (e: Exception) {
        // 如果 keytool 找不到，可能是环境问题，抛出更友好的错误
        throw RuntimeException("Could not run keytool command. Details: ${e.message}")
    }
}


android {
    namespace = "com.gk.movie"
    compileSdk = 36 
    
    // 推荐：指定 NDK 版本
    ndkVersion = "29.0.14033849" 

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
        
        ndk {
            // 只打包 64 位架构
            abiFilters.add("arm64-v8a")
        }
    }
   
    // --- 3. 签名配置 ---
    signingConfigs {
        create("release") {
            storeFile = keystoreFile
            storePassword = keyPwd
            keyAlias = keyAliasName
            keyPassword = keyPwd
            
            // 显式开启 v1, v2, v3, v4 签名
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }
    
    // --- 新增：关闭 Release 构建的 Lint 检查 ---
    lint { 
        checkReleaseBuilds = false
        abortOnError = false
    }
    
    buildTypes {
        release {
            // 应用签名配置
            signingConfig = signingConfigs.getByName("release")
            
            isMinifyEnabled = true  // 启用代码压缩
            isShrinkResources = true  // 启用资源压缩
            applicationIdSuffix = ".release"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            applicationIdSuffix = ".debug"
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
            // 安全的基础排除配置
            excludes += setOf(
                "/META-INF/AL2.0",
                "/META-INF/LGPL2.1",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "**/*.proto",
                "**/*.debugInfo",
                "**/DebugProbesKt.bin"
            )
            
            // 合并服务配置文件
            merges += "META-INF/services/**"
            
            // 选择第一个遇到的共享库
            pickFirsts += setOf(
                "**/libc++_shared.so",
                "**/libjni.so")
        }
    }
}

kotlin {
    sourceSets.all {
        // 开启 Glide Compose 和 Material3 的实验性 API 支持
        languageSettings.optIn("androidx.compose.material3.ExperimentalMaterial3Api")
        languageSettings.optIn("com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi")
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
