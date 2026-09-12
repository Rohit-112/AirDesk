import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinCocoapods)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "com.share.app.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Android and iOS share the webrtc-kmp transport and the camera scanner;
    // desktop has its own implementations of both.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("mobile") {
                withCompilations { it.target.platformType == KotlinPlatformType.androidJvm }
                group("ios")
            }
        }
    }

    cocoapods {
        summary = "Knotic shared UI and sync engine"
        homepage = "https://getknotic.web.app"
        version = "1.0.1"
        ios.deploymentTarget = "15.0"
        podfile = project.file("../iosApp/Podfile")

        framework {
            baseName = "Shared"
            isStatic = true
        }

        // The Kotlin bindings already ship inside the GitLive and webrtc-kmp
        // klibs; the pods only have to be linked into the app.
        pod("FirebaseCore") { linkOnly = true }
        pod("FirebaseAuth") { linkOnly = true }
        pod("FirebaseDatabase") { linkOnly = true }
        pod("FirebaseStorage") { linkOnly = true }
        pod("WebRTC-SDK") {
            version = "125.6422.07"
            moduleName = "WebRTC"
            linkOnly = true
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-opt-in=kotlin.uuid.ExperimentalUuidApi",
            "-opt-in=kotlin.time.ExperimentalTime",
        )
    }

    sourceSets {
        commonMain.dependencies {
            // Compose
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)

            // Architecture
            implementation(libs.jb.lifecycle.viewmodel.compose)
            implementation(libs.jb.lifecycle.runtime.compose)
            implementation(libs.jb.navigation.compose)
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Kotlin
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            // Data
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.gitlive.firebase.app)
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.database)
            implementation(libs.gitlive.firebase.storage)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)

            // Platform services
            implementation(libs.qrose)
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs)
            implementation(libs.kermit)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        val mobileMain by getting {
            dependencies {
                implementation(libs.webrtc.kmp)
                implementation(libs.easyqrscan)
            }
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
            implementation(libs.androidx.core.ktx)
            api(libs.androidx.activity.compose)
        }

        jvmMain.dependencies {
            implementation(libs.webrtc.java)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}
