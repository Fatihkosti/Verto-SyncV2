plugins {
    id("verto.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.verto.core.session"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.core)
}
