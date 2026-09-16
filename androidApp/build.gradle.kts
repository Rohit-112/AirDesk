import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
    // Also writes the build id the Crashlytics SDK refuses to start without.
    alias(libs.plugins.crashlytics)
}

android {
    namespace = "com.share.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // The id this app is registered under in the Firebase console. It differs
        // from the namespace above, which stays with the Kotlin package.
        applicationId = "com.sharing.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Both come from the catalog, which `checkVersion` holds Brand.kt and
        // the iOS project to.
        versionCode = libs.versions.app.build.get().toInt()
        versionName = libs.versions.app.version.get()
    }

    buildTypes {
        debug {
            // Debug builds never report (src/debug/AndroidManifest.xml turns both
            // SDKs off), so there is nothing to symbolicate.
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // R8 renames everything, so a release crash report is unreadable
            // without the mapping file.
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = true
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(projects.shared)
}
