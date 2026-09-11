plugins {
    id("verto.android.library")
    id("verto.android.compose")
    id("verto.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.verto.feature.dashboard" }

dependencies {
    api(project(":feature:dashboard:api"))
    implementation(project(":core:common"))
    implementation(project(":core:crash"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:session"))
    implementation(project(":core:audit"))
    implementation(project(":data:database"))
    implementation(project(":data:network"))
    implementation(project(":data:sync"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.supabase.postgrest)
    implementation(platform(libs.supabase.bom))
    implementation(libs.kotlinx.serialization.core)
    testImplementation(libs.junit)
}
