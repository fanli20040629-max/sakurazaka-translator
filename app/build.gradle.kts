plugins {
    id("com.android.application")
}

android {
    namespace = "com.fanli.sakurazakatranslator"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fanli.sakurazakatranslator"
        minSdk = 34
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.1-probe-review"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    signingConfigs {
        getByName("debug") {
            // Preserve the original machine's key when present. New checkouts use AGP's default.
            val localDebugKey = rootProject.file("debug.keystore")
            if (localDebugKey.isFile) {
                storeFile = localDebugKey
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
}
