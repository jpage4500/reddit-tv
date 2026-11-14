plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
}

subprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.0")
            force("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
        }
    }
}
