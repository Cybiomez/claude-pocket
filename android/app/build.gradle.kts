plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Версия из git-тега: v0.2.0 -> versionName 0.2.0, versionCode 200.
// Pre-release: v0.4.0-dev.1 -> versionName 0.4.0-dev.1, versionCode по базовой 0.4.0 (400).
// Без тега (локальная dev-сборка): 0.0.0-dev / 1.
fun gitTagVersion(): Pair<String, Int> {
    return try {
        val out = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
            .directory(rootDir).redirectErrorStream(true).start()
            .inputStream.bufferedReader().readText().trim()
        // Суффикс -dev.N и т.п. попадает в versionName, но не в versionCode:
        // versionCode считается по базовой X.Y.Z, чтобы pre-release вставал поверх прошлого релиза
        val m = Regex("^v(\\d+)\\.(\\d+)\\.(\\d+)(-[0-9A-Za-z.]+)?$").find(out) ?: return "0.0.0-dev" to 1
        val maj = m.groupValues[1]; val min = m.groupValues[2]; val pat = m.groupValues[3]
        val suffix = m.groupValues[4]
        "$maj.$min.$pat$suffix" to (maj.toInt() * 10000 + min.toInt() * 100 + pat.toInt())
    } catch (_: Exception) { "0.0.0-dev" to 1 }
}
val (verName, verCode) = gitTagVersion()

android {
    namespace = "dev.claudepocket"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.claudepocket"
        minSdk = 26
        targetSdk = 35
        versionCode = verCode
        versionName = verName
    }

    // Ключ подписи приходит извне (не хранится в репо):
    //  - CI: секреты KEYSTORE_* → env
    //  - локально: android/keystore/claude-pocket.keystore (в .gitignore)
    val ksPath = System.getenv("KEYSTORE_PATH") ?: "../keystore/claude-pocket.keystore"
    val ksFile = file(ksPath)
    signingConfigs {
        create("release") {
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "claudepocket"
                keyAlias = System.getenv("KEY_ALIAS") ?: "claudepocket"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "claudepocket"
            }
        }
    }

    buildTypes {
        release {
            // Без minify: не воюем с R8-правилами для JSch/BouncyCastle, размер не критичен
            isMinifyEnabled = false
            if (ksFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0", "META-INF/LGPL2.1",
            "META-INF/versions/**", "META-INF/INDEX.LIST",
            "META-INF/BC1024KE.SF", "META-INF/BC1024KE.DSA", "META-INF/BC2048KE.SF", "META-INF/BC2048KE.DSA",
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    // SSH: активный форк JSch + BouncyCastle для ed25519-ключей
    implementation("com.github.mwiede:jsch:0.2.21")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
}
