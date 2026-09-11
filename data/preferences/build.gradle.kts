plugins {
    id("verto.android.library")
    id("verto.android.hilt")
}

android {
    namespace = "com.verto.data.preferences"
}

dependencies {
    implementation(project(":core:common"))
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
}
