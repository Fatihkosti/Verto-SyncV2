plugins {
    id("verto.android.library")
    id("verto.android.compose")
    id("verto.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.verto.feature.invoice" }

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:crash"))
    implementation(project(":core:export"))
    implementation(project(":feature:dashboard:api"))
    implementation(project(":core:common"))
    implementation(project(":core:session"))
    implementation(project(":core:audit"))
    implementation(project(":data:database"))
    implementation(project(":data:network"))
    implementation(project(":data:sync"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
