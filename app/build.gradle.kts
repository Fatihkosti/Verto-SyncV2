import java.util.Properties

plugins {
    id("verto.android.application")
    id("verto.android.compose")
    id("verto.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

// Firebase runtime dependencies remain available, but the Google Services
// resource processor is only applicable when the project-specific config exists.
val disableFirebaseBuildPlugins = providers.environmentVariable("DISABLE_FIREBASE_BUILD_PLUGINS")
    .orNull?.equals("true", ignoreCase = true) == true
if (file("google-services.json").isFile && !disableFirebaseBuildPlugins) {
    pluginManager.apply("com.google.gms.google-services")
    pluginManager.apply("com.google.firebase.crashlytics")
}

val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) props.load(f.inputStream())
}

fun secureProperty(name: String): String =
    providers.environmentVariable(name).orNull?.trim().orEmpty()
        .ifBlank { localProps.getProperty(name, "").trim() }

val releaseStoreFile = secureProperty("VERT_RELEASE_STORE_FILE")
val releaseStorePassword = secureProperty("VERT_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = secureProperty("VERT_RELEASE_KEY_ALIAS")
val releaseKeyPassword = secureProperty("VERT_RELEASE_KEY_PASSWORD")
val releaseStoreType = secureProperty("VERT_RELEASE_STORE_TYPE").ifBlank { "JKS" }
val supabaseUrl = secureProperty("SUPABASE_URL")
val supabaseAnonKey = secureProperty("SUPABASE_ANON_KEY")
val resolvedReleaseStoreFile = releaseStoreFile.takeIf { it.isNotBlank() }?.let { path ->
    rootProject.file(path).takeIf { it.isAbsolute || it.exists() } ?: file(path)
}
val hasReleaseSigningConfig = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { it.isNotBlank() }
val hasSupabaseReleaseConfig = listOf(
    supabaseUrl,
    supabaseAnonKey
).all { it.isNotBlank() }

gradle.taskGraph.whenReady {
    val isReleaseBuild = allTasks.any { task ->
        task.path.startsWith(":app:") && task.name.contains("Release")
    }
    if (isReleaseBuild && !hasReleaseSigningConfig) {
        throw GradleException(
            "Release signing is not configured. Set VERT_RELEASE_STORE_FILE, " +
                "VERT_RELEASE_STORE_PASSWORD, VERT_RELEASE_KEY_ALIAS, and " +
                "VERT_RELEASE_KEY_PASSWORD through environment variables or local.properties."
        )
    }
    if (isReleaseBuild && resolvedReleaseStoreFile?.isFile != true) {
        throw GradleException("Release keystore file does not exist: $releaseStoreFile")
    }
    if (isReleaseBuild && !hasSupabaseReleaseConfig) {
        throw GradleException(
            "Supabase release configuration is missing. Set SUPABASE_URL and " +
                "SUPABASE_ANON_KEY in local.properties before building release."
        )
    }
}

android {
    namespace = "com.verto.app"

    defaultConfig {
        applicationId = "com.verto.app"
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigningConfig) {
                storeFile = resolvedReleaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeType = releaseStoreType
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            if (gradle.startParameter.isOffline) {
                matchingFallbacks += listOf("debug")
            }
            isDebuggable = false
            isJniDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

tasks.register("releaseCandidate") {
    group = "build"
    description = "Builds signed, minified APK and AAB release artifacts."
    dependsOn("assembleRelease", "bundleRelease")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:crash"))
    implementation(project(":core:session"))
    implementation(project(":core:audit"))
    implementation(project(":core:export"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:inventory"))
    implementation(project(":feature:party"))
    implementation(project(":feature:invoice"))
    implementation(project(":feature:payment"))
    implementation(project(":feature:shipment"))
    implementation(project(":feature:reports"))
    implementation(project(":feature:organization"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:notifications"))
    implementation(project(":feature:commission"))
    implementation(project(":feature:messages"))
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:expenses"))
    implementation(project(":feature:management"))
    implementation(project(":feature:integration:optimal"))
    implementation(project(":data:database"))
    implementation(project(":data:preferences"))
    implementation(project(":data:network"))
    implementation(project(":data:sync"))
    implementation(project(":data:operations"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)

    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)


    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.crashlytics.ktx)

    implementation(libs.coil.compose)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.paging.compose)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
