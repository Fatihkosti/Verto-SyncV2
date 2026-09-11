plugins {
    id("verto.android.library")
}

android {
    namespace = "com.verto.core.crash"
}

dependencies {
    implementation(project(":core:common"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics.ktx)
}
