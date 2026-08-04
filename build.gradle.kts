// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    dependencies {
        // AGP 9 has built-in Kotlin support and ships with KGP 2.2.10. Declaring the
        // classpath here is the documented way to run a newer Kotlin Gradle Plugin.
        // Keep this in sync with `kotlin` in gradle/libs.versions.toml -- version
        // catalog accessors are not available inside a buildscript block.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
