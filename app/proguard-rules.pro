# ==========================================
# 极光影视 ProGuard / R8 混淆规则配置
# ==========================================

# 1. 保留基本的行号信息，方便线上查崩溃日志
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ==========================================
# ★ 核心 1：JNA & UniFFI 跨语言调用防混淆 (绝对不能动)
# ==========================================
# 保护 JNA 核心类，防止 native 方法绑定失败抛出 UnsatisfiedLinkError
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

# 保护 UniFFI 自动生成的 Kotlin 接口代码
-keep class uniffi.** { *; }
-keep class uniffi.m3u8_parser.** { *; }

# ==========================================
# ★ 核心 2：JSON 数据解析实体类防混淆
# ==========================================
# 保护所有被 kotlinx.serialization 标记的类
-keepattributes *Annotation*, InnerClasses
-keep @kotlinx.serialization.Serializable class * { *; }

# 手动保护我们写的数据实体类（防止序列化失败导致闪退）
-keep class com.gk.movie.CmsVod { *; }
-keep class com.gk.movie.Utils.Search.Util.SearchMovie { *; }
-keep class com.gk.movie.Utils.Search.Util.EngineItem { *; }
-keep class com.gk.movie.Utils.Search.Util.SearchPageData { *; }
-keep class com.gk.movie.Utils.Category.Util.PageItem { *; }
-keep class com.gk.movie.Utils.Category.Util.CategoryMovie { *; }

# ==========================================
# ★ 核心 3：OkHttp & Glide 网络防混淆
# ==========================================
# 保护 Glide 自动生成的模块
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl { *; }
-keep class com.gk.movie.Utils.okhttpUtils.GlideModuleUtil { *; }

# ==========================================
# ★ Jsoup 原有规则
# ==========================================
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern