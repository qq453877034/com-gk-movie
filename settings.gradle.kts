pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // ★ 添加 JitPack 插件仓库
        maven { url = uri("https://jitpack.io") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // ★ 添加 JitPack 依赖仓库，专门负责下载 GitHub 上的第三方库！
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "GKMovie"

include(":app")