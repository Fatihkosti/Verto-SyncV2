plugins {
    id("verto.android.library")
}

android {
    namespace = "com.verto.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
