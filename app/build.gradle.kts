plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hossa.redoble"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hossa.redoble"
        minSdk = 26
        targetSdk = 36
        versionCode = 48
        versionName = "10.0"
    }

    // Firma: si existen las variables (GitHub Secrets), se usan; si no, la clave
    // actual del repo para que las actualizaciones sigan instalándose encima.
    // PENDIENTE antes de publicar: clave nueva solo en Secrets y Play App Signing.
    signingConfigs {
        val ksFile = System.getenv("PRISMA_KEYSTORE")
        create("release") {
            storeFile = file(ksFile ?: "redoble.keystore")
            storePassword = System.getenv("PRISMA_KEYSTORE_PASSWORD") ?: "redoble123"
            keyAlias = System.getenv("PRISMA_KEY_ALIAS") ?: "redoble"
            keyPassword = System.getenv("PRISMA_KEY_PASSWORD") ?: "redoble123"
        }
        getByName("debug") {
            storeFile = file("redoble.keystore")
            storePassword = "redoble123"
            keyAlias = "redoble"
            keyPassword = "redoble123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    lint { abortOnError = true; checkReleaseBuilds = true; warningsAsErrors = false }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
