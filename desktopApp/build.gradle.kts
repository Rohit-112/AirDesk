import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// webrtc-java ships its native library per operating system, as a classifier.
val webrtcNativeClassifier: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val arm = arch.contains("aarch64") || arch.contains("arm64")
    when {
        os.contains("win") -> "windows-x86_64"
        os.contains("mac") -> if (arm) "macos-aarch64" else "macos-x86_64"
        else -> if (arm) "linux-aarch64" else "linux-x86_64"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(projects.shared)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    runtimeOnly("${libs.webrtc.java.get()}:$webrtcNativeClassifier")
}

compose.desktop {
    application {
        mainClass = "com.share.app.desktop.MainKt"

        // The app is a window that waits for the user, not a workload. The
        // serial collector starts quicker and keeps fewer threads alive, and a
        // small starting heap avoids growing one during startup - the busiest
        // moment there is.
        jvmArgs += listOf("-XX:+UseSerialGC", "-Xms32m")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Knotic"
            packageVersion = libs.versions.app.version.get()
            description = "Send files and text between your devices"
            vendor = "Knotic"

            windows {
                iconFile.set(project.file("icons/knotic.ico"))
                menuGroup = "Knotic"
                // Stable, so an .msi upgrades in place instead of installing twice.
                upgradeUuid = "6d2f1b7a-0f4e-4a1d-9a57-2b1c7f9d3e41"
            }
            linux {
                iconFile.set(project.file("icons/knotic.png"))
            }
            // firebase-java-sdk and webrtc-java reach into these at runtime.
            modules("java.sql", "java.naming", "jdk.unsupported", "java.net.http")
        }
    }
}
