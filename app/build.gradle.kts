plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
    alias(libs.plugins.hilt)
    id("kotlin-kapt")
}

android {
    namespace = "com.testproject"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.testproject"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        dataBinding = true
        viewBinding = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)                  // Core Kotlin extensions
    implementation(libs.androidx.lifecycle.runtime.ktx)     // Lifecycle aware components
    implementation(libs.androidx.lifecycle.viewmodel.ktx)   // ViewModel support
    implementation(libs.androidx.appcompat)                 // AppCompat support
    implementation(libs.material)                           // Material Components
    implementation(libs.androidx.activity)                  // Activity KTX
    implementation(libs.androidx.fragment)                  // Fragment KTX
    implementation(libs.androidx.constraintlayout)          // ConstraintLayout
    implementation(libs.androidx.recyclerview)              // RecyclerView

    // DrawerLayout (REQUIRED for NavigationUI with drawers)
    implementation(libs.androidx.drawerlayout)

    implementation(libs.androidx.navigation.fragment)       // Fragment navigation host
    implementation(libs.androidx.navigation.ui.ktx)         // NavigationUI (ActionBar, DrawerLayout)

    implementation(platform(libs.firebase.bom))              // Firebase BOM for version alignment
    implementation(libs.firebase.analytics)                 // Firebase Analytics
    implementation(libs.firebase.crashlytics)               // Crashlytics
    implementation(libs.firebase.database)                  // Realtime Database

    implementation(libs.retrofit)                           // Retrofit client
    implementation(libs.converter.gson)                     // Gson converter
    implementation(libs.okhttp)                             // OkHttp HTTP client
    implementation(libs.logging.interceptor)                // Logging interceptor

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // JSON Parsing
    implementation(libs.gson)

    // Dependency Injection - Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
