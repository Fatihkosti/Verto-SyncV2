plugins {
    id("verto.android.library")
    id("verto.android.compose")
    id("verto.android.hilt")
}

android { namespace = "com.verto.feature.reports" }

dependencies {
    implementation(project(":core:audit"))
    implementation(project(":core:session"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:export"))
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.vico.compose.m3)
    implementation(libs.vico.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
}
