import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // 启用 Compose 编译器插件
    alias(libs.plugins.kotlin.compose)
    // 启用 JSON 序列化插件
    alias(libs.plugins.kotlin.serialization)
    // ★ 新增：启用 KAPT 插件
    kotlin("kapt")
}

// --- 1. 定义变量 ---
val keystoreFile = file("release.keystore")
val keyPwd = "android"
val keyAliasName = "key0" 

// --- 2. 自动生成 Keystore ---
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
            throw RuntimeException("Failed to generate keystore.\nExit code: $exitCode\nOutput: $output")
        } else {
            println("Keystore generated successfully.")
        }
    } catch (e: Exception) {
        throw RuntimeException("Could not run keytool command. Details: ${e.message}")
    }
}

android {
    namespace = "com.gk.movie"
    compileSdk = 36 
    
    ndkVersion = "29.0.14033849" 

    defaultConfig {
        applicationId = "com.gk.movie"
        minSdk = 26 
        targetSdk = 36
        versionCode = 400
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }
   
    signingConfigs {
        create("release") {
            storeFile = keystoreFile
            storePassword = keyPwd
            keyAlias = keyAliasName
            keyPassword = keyPwd
            
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }
    
    lint { 
        checkReleaseBuilds = false
        abortOnError = false
    }
    
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true  
            isShrinkResources = true  
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
            merges += "META-INF/services/**"
            pickFirsts += setOf(
                "**/libc++_shared.so",
                "**/libjni.so")
        }
    }
}

kotlin {
    sourceSets.all {
        languageSettings.optIn("androidx.compose.material3.ExperimentalMaterial3Api")
        languageSettings.optIn("com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.material3.window.size)

    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation)

    implementation(libs.okhttp)
    implementation(libs.glide.compose)
    
    // ★ 新增：Glide OkHttp3 引擎整合包与 KAPT 编译器处理器
    implementation(libs.glide)
    implementation(libs.glide.okhttp3.integration)
    kapt(libs.glide.compiler)

    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    
    implementation(libs.dev.chrisbanes.haze)
    implementation(libs.dev.chrisbanes.haze.materials)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.decoder)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.media3.ui.compose.material3)
    implementation(libs.androidx.media3.datasource.okhttp)
    
    implementation(libs.org.jellyfin.media3.ffmpeg)  
    
    debugImplementation(libs.androidx.ui.tooling)
    
    // ✅ 正确的写法（加上 @aar）
    implementation("net.java.dev.jna:jna:5.18.1@aar")
}