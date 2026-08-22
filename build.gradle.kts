// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.navigation.safeargs) apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version "1.9.22" apply false
    alias(libs.plugins.google.services) apply false
}