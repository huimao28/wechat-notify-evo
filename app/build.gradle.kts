plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes from CI secrets (see .github/workflows/release.yml).
// Unset outside CI: debug builds are unaffected, release builds fail loudly.
val releaseKeystoreFile = System.getenv("KEYSTORE_FILE")?.let { file(it) }

// Detect the keystore format from its content (JKS magic vs PKCS12 ASN.1),
// so JKS and .p12 keystores both work without extra configuration.
val releaseKeystoreType: String? = releaseKeystoreFile?.takeIf { it.exists() }?.let { ks ->
    val header = ks.inputStream().use { it.readNBytes(4) }
    if (header.size == 4 && header[0] == 0xFE.toByte() && header[1] == 0xED.toByte() &&
        header[2] == 0xFE.toByte() && header[3] == 0xED.toByte()
    ) "JKS" else "PKCS12"
}

android {
    namespace = "com.huimao.wechatnotifyevo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.huimao.wechatnotifyevo"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.0.2"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseKeystoreFile
            storeType = releaseKeystoreType
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "2.0.21"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
