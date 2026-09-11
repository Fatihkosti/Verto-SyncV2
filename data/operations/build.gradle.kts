plugins {
    id("verto.android.library")
    id("verto.android.hilt")
}

android {
    namespace = "com.verto.data.operations"
    defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:audit"))
    implementation(project(":core:session"))
    implementation(project(":data:database"))
    implementation(project(":data:preferences"))
    implementation(project(":data:network"))
    implementation(project(":data:sync"))
    implementation(project(":feature:invoice"))
    implementation(project(":feature:payment"))
    implementation(project(":feature:organization"))
    implementation(project(":feature:reports"))
    implementation(project(":feature:inventory"))
    implementation(project(":feature:party"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(libs.androidx.test.core.ktx)
}
