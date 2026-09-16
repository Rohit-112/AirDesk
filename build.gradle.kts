plugins {
    // Applied in the modules that need them; declared once here so every module
    // resolves the same plugin versions.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinCocoapods) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.crashlytics) apply false
}

/**
 * The version lives in more than one place by necessity: Gradle reads the
 * catalog, the footer reads Brand.VERSION, and Xcode reads its own project
 * file and Info.plist. They drift silently, and an app claiming the wrong
 * version is worse than one claiming none - so the tests refuse to run until
 * they agree. The web client's `npm run check:version` does the same job.
 */
val checkVersion by tasks.registering {
    group = "verification"
    description = "Fails when Brand.kt or the iOS project disagree with the catalog's app version."

    val version = libs.versions.app.version.get()
    val build = libs.versions.app.build.get()
    val brand = layout.projectDirectory.file("shared/src/commonMain/kotlin/com/share/app/config/Brand.kt")
    val plist = layout.projectDirectory.file("iosApp/iosApp/Info.plist")
    val xcode = layout.projectDirectory.file("iosApp/iosApp.xcodeproj/project.pbxproj")
    inputs.files(brand, plist, xcode)

    doLast {
        val problems = mutableListOf<String>()
        if (!Regex("""\d+\.\d+\.\d+""").matches(version)) {
            problems += "app-version \"$version\" is not a plain x.y.z"
        }

        val brandVersion = Regex("""VERSION\s*=\s*"([^"]+)"""").find(brand.asFile.readText())?.groupValues?.get(1)
        if (brandVersion != version) problems += "Brand.VERSION is $brandVersion"

        // Info.plist keeps each value on the line after its key.
        val plistText = plist.asFile.readText()
        fun plistValue(key: String) =
            Regex("""<key>$key</key>\s*<string>([^<]*)</string>""").find(plistText)?.groupValues?.get(1)
        val shortVersion = plistValue("CFBundleShortVersionString")
        val bundleVersion = plistValue("CFBundleVersion")
        if (shortVersion != version) problems += "Info.plist CFBundleShortVersionString is $shortVersion"
        if (bundleVersion != build) problems += "Info.plist CFBundleVersion is $bundleVersion"

        // One entry per build configuration; every one of them has to match.
        val xcodeText = xcode.asFile.readText()
        fun xcodeValues(key: String) = Regex("""$key = ([^;]+);""").findAll(xcodeText).map { it.groupValues[1] }.toSet()
        xcodeValues("MARKETING_VERSION").filter { it != version }
            .forEach { problems += "project.pbxproj MARKETING_VERSION is $it" }
        xcodeValues("CURRENT_PROJECT_VERSION").filter { it != build }
            .forEach { problems += "project.pbxproj CURRENT_PROJECT_VERSION is $it" }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "Version check failed - the catalog says $version ($build), but:\n" +
                    problems.joinToString("\n") { "  - $it" } +
                    "\nBump them together, or the app will report the wrong release.",
            )
        }
        logger.lifecycle("version ok: $version ($build)")
    }
}
