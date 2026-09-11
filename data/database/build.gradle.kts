import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("verto.android.library")
    id("verto.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xjvm-default=all")
}

android {
    namespace = "com.verto.data.database"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets {
        getByName("androidTest").assets.srcDir(rootProject.file("app/schemas"))
    }
}

ksp {
    arg("room.schemaLocation", rootProject.file("app/schemas").path)
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:audit"))

    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    api(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
