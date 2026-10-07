buildscript {
    dependencies {
        // AGP 9's built-in Kotlin bundles an older KGP by default; supabase-kt 3.8.0
        // ships metadata that requires a newer Kotlin compiler to read, so we pin
        // the Kotlin Gradle Plugin version explicitly here.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
