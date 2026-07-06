plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    targetCompatibility = JavaVersion.VERSION_21
    sourceCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":ksp-annotations"))
    implementation(libs.ksp.symbol.processing.api)
}
