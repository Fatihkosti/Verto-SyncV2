import java.util.Properties

plugins {
    id("verto.android.library")
    id("verto.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().also { props ->
    val file = rootProject.file("local.properties")
    if (file.exists()) props.load(file.inputStream())
}

fun secureProperty(name: String): String =
    providers.environmentVariable(name).orNull?.trim().orEmpty()
        .ifBlank { localProps.getProperty(name, "").trim() }

android {
    namespace = "com.verto.data.network"
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", "\"${secureProperty("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secureProperty("SUPABASE_ANON_KEY")}\"")
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:session"))
    implementation(project(":data:database"))
    implementation(project(":data:preferences"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.core)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.utils)
    testImplementation(libs.junit)
}
