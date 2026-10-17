import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val releaseStoreFilePath = localProperties.getProperty("releaseStoreFile")
val releaseStorePassword = localProperties.getProperty("releaseStorePassword")
val releaseKeyAlias = localProperties.getProperty("releaseKeyAlias")
val releaseKeyPassword = localProperties.getProperty("releaseKeyPassword")
val hasReleaseSigning = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

// GitHub Actions run number: makes every CI build strictly newer than the last
// one (versionCode) and shows which build is installed (versionName).
val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.sentry)
    alias(libs.plugins.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.firebase)
}

android {
    namespace = "network.mysterium.provider"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "network.mysterium.provider"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // Base 100 keeps it above the previously released custom builds (12).
        versionCode = 100 + (ciRunNumber ?: 0)
        versionName = if (ciRunNumber != null) "5.2.3-custom.$ciRunNumber" else "5.2.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Real phones only: drop the x86/x86_64 copies of the Go node to shrink
        // the APK a lot. Both ARM ABIs stay so 32-bit phones still install.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        manifestPlaceholders["SENTRY"] = localProperties.getProperty("sentry") ?: ""
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation(project(":node"))

    api(platform(libs.androidx.compose.bom))

    implementation(libs.bundles.core)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.accompanist.tools)
    implementation(libs.bundles.navigation)
    implementation(libs.bundles.di)
    implementation(libs.markdown)
    implementation(libs.bundles.retrofit)
    implementation(libs.bundles.serialization)

    debugImplementation(libs.bundles.compose.debug)
}
