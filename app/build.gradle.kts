plugins {
    id("com.android.application")
}

android {
    namespace = "com.windymaster.khmersync"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.windymaster.khmersync"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
