plugins {
    id("verto.android.library")
    id("verto.android.compose")
    id("verto.android.hilt")
}

android { namespace = "com.verto.feature.profile" }

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:crash"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
}
