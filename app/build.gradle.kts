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
        versionCode = 3
        versionName = "0.3.0-probe-select"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
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
