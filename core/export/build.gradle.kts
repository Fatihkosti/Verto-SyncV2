plugins {
    id("verto.android.library")
}

android {
    namespace = "com.verto.core.export"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.core.ktx)
}
