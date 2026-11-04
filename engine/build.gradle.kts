plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Java 17 bytecode from whatever JDK runs Gradle (17 here, 21 on the F-Droid build server);
// a toolchain would demand an installed JDK 17.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
