plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hossa.redoble"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hossa.redoble"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "5.2"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("redoble.keystore")
            storePassword = "redoble123"
            keyAlias = "redoble"
            keyPassword = "redoble123"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
