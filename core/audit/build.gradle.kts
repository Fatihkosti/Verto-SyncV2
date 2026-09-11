plugins {
    id("verto.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.verto.core.audit"
}

dependencies {
    api(project(":core:session"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
}
