import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.roborazzi)
}

// Tests/lint never open signing files. The gate may assemble an unsigned release APK.
val requestedTasks = gradle.startParameter.taskNames.map { it.substringAfterLast(':').lowercase() }
val releaseBundleRequested = requestedTasks.any { it == "bundlerelease" || it == "bundle" }
val releasePackagingRequested = releaseBundleRequested || requestedTasks.any { it == "assemblerelease" || it == "assemble" }
val releaseSigning = if (releasePackagingRequested) {
    val path = providers.gradleProperty("freeperiod.signing").orNull
        ?: providers.environmentVariable("FREEPERIOD_SIGNING_PROPERTIES").orNull
    if (path.isNullOrBlank()) {
        if (releaseBundleRequested) throw GradleException("bundleRelease requires external signing properties: -Pfreeperiod.signing=<path> or FREEPERIOD_SIGNING_PROPERTIES.")
        null
    } else {
        val propertiesFile = file(path).canonicalFile
        if (propertiesFile.toPath().startsWith(rootDir.canonicalFile.toPath()))
            throw GradleException("Release signing properties must be outside the repository.")
        if (!propertiesFile.isFile) throw GradleException("External release signing properties file was not found.")
        Properties().apply {
            propertiesFile.inputStream().use { load(it) }
            listOf("storeFile", "storePassword", "keyAlias", "keyPassword").forEach {
                if (getProperty(it).isNullOrBlank()) throw GradleException("Release signing properties require $it.")
            }
            val keystore = File(getProperty("storeFile")).let {
                (if (it.isAbsolute) it else File(propertiesFile.parentFile, it.path)).canonicalFile
            }
            if (keystore.toPath().startsWith(rootDir.canonicalFile.toPath()))
                throw GradleException("Release keystore must be outside the repository.")
            setProperty("storeFile", keystore.path)
        }
    }
} else null

val commitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().ifEmpty { "0" }

android {
    namespace = "org.freeperiod.app"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "org.freeperiod.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.0"
    }

    signingConfigs {
        if (releaseSigning != null) create("externalRelease") {
            storeFile = file(releaseSigning.getProperty("storeFile"))
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // Each test APK gets an ascending developer number (commit count), e.g. 1.1.0-dev.57.
            versionNameSuffix = "-dev.$commitCount"
        }
        release {
            if (releaseSigning != null) signingConfig = signingConfigs.getByName("externalRelease")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    buildFeatures {
        compose = true
    }

    // Room MigrationTestHelper (Robolectric) reads exported schemas from the app's assets;
    // debug only, never shipped in release.
    sourceSets["debug"].assets.srcDir("$projectDir/schemas")

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    androidResources {
        // Only the languages we ship; keeps library translations out of the APK.
        localeFilters += listOf("en", "de")
    }
}

kotlin {
    jvmToolchain(17)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.biometric)
    // biometric 1.1.0 pulls fragment 1.2.5, which rejects activity-result request codes (permission crash).
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.room.testing)
    testImplementation(libs.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
}
