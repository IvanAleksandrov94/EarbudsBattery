plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.grapesapps.earbudsbattery"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.grapesapps.earbudsbattery"
        minSdk = 31
        targetSdk = 36
        // Fixed at the user's request. Do not increment these values.
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Local installable build, using the same development certificate as version 1.0.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("com.kieronquinn.smartspacer:sdk-plugin:1.1.2") {
        exclude(group = "androidx.profileinstaller")
    }
    testImplementation(libs.junit)
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
