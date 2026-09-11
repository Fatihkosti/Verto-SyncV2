import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.detekt) apply false
}

val detektVersion = libs.versions.detekt.get()

subprojects {
    pluginManager.apply("io.gitlab.arturbosch.detekt")

    // The local dependency cache contains the Android debug publications for
    // Supabase 3.0.2, but not their release publications. Reuse those exact
    // cached artifacts only for explicitly offline builds; online builds keep
    // the normal variant selection.
    if (gradle.startParameter.isOffline) {
        configurations.configureEach {
            resolutionStrategy.dependencySubstitution {
                substitute(module("io.github.jan-tennert.supabase:auth-kt-android:3.0.2"))
                    .using(variant(module("io.github.jan-tennert.supabase:auth-kt-android-debug:3.0.2")) {
                        attributes {
                            attribute(org.gradle.api.attributes.Attribute.of("com.android.build.api.attributes.BuildTypeAttr", String::class.java), "debug")
                        }
                    })
                substitute(module("io.github.jan-tennert.supabase:functions-kt-android:3.0.2"))
                    .using(variant(module("io.github.jan-tennert.supabase:functions-kt-android-debug:3.0.2")) {
                        attributes {
                            attribute(org.gradle.api.attributes.Attribute.of("com.android.build.api.attributes.BuildTypeAttr", String::class.java), "debug")
                        }
                    })
                substitute(module("io.github.jan-tennert.supabase:postgrest-kt-android:3.0.2"))
                    .using(variant(module("io.github.jan-tennert.supabase:postgrest-kt-android-debug:3.0.2")) {
                        attributes {
                            attribute(org.gradle.api.attributes.Attribute.of("com.android.build.api.attributes.BuildTypeAttr", String::class.java), "debug")
                        }
                    })
                substitute(module("io.github.jan-tennert.supabase:realtime-kt-android:3.0.2"))
                    .using(variant(module("io.github.jan-tennert.supabase:realtime-kt-android-debug:3.0.2")) {
                        attributes {
                            attribute(org.gradle.api.attributes.Attribute.of("com.android.build.api.attributes.BuildTypeAttr", String::class.java), "debug")
                        }
                    })
                substitute(module("io.github.jan-tennert.supabase:storage-kt-android:3.0.2"))
                    .using(variant(module("io.github.jan-tennert.supabase:storage-kt-android-debug:3.0.2")) {
                        attributes {
                            attribute(org.gradle.api.attributes.Attribute.of("com.android.build.api.attributes.BuildTypeAttr", String::class.java), "debug")
                        }
                    })
                substitute(module("io.github.jan-tennert.supabase:supabase-kt-android:3.0.2"))
                    .using(variant(module("io.github.jan-tennert.supabase:supabase-kt-android-debug:3.0.2")) {
                        attributes {
                            attribute(org.gradle.api.attributes.Attribute.of("com.android.build.api.attributes.BuildTypeAttr", String::class.java), "debug")
                        }
                    })
                substitute(module("com.russhwolf:multiplatform-settings-android:1.2.0"))
                    .using(variant(module("com.russhwolf:multiplatform-settings-android-debug:1.2.0")) {
                        attributes {
                            attribute(org.gradle.api.attributes.Attribute.of("com.android.build.api.attributes.BuildTypeAttr", String::class.java), "debug")
                        }
                    })
                substitute(module("com.russhwolf:multiplatform-settings-coroutines-android:1.2.0"))
                    .using(variant(module("com.russhwolf:multiplatform-settings-coroutines-android-debug:1.2.0")) {
                        attributes {
                            attribute(org.gradle.api.attributes.Attribute.of("com.android.build.api.attributes.BuildTypeAttr", String::class.java), "debug")
                        }
                    })
                substitute(module("com.russhwolf:multiplatform-settings-no-arg-android:1.2.0"))
                    .using(variant(module("com.russhwolf:multiplatform-settings-no-arg-android-debug:1.2.0")) {
                        attributes {
                            attribute(org.gradle.api.attributes.Attribute.of("com.android.build.api.attributes.BuildTypeAttr", String::class.java), "debug")
                        }
                    })
                substitute(module("co.touchlab:kermit-android:2.0.4"))
                    .using(variant(module("co.touchlab:kermit-android-debug:2.0.4")) {
                        attributes {
                            attribute(org.gradle.api.attributes.Attribute.of("com.android.build.api.attributes.BuildTypeAttr", String::class.java), "debug")
                        }
                    })
                substitute(module("co.touchlab:kermit-core-android:2.0.4"))
                    .using(variant(module("co.touchlab:kermit-core-android-debug:2.0.4")) {
                        attributes {
                            attribute(org.gradle.api.attributes.Attribute.of("com.android.build.api.attributes.BuildTypeAttr", String::class.java), "debug")
                        }
                    })
                substitute(module("com.soywiz.korlibs.krypto:krypto-android:4.0.10"))
                    .using(variant(module("com.soywiz.korlibs.krypto:krypto-android:4.0.10")) {
                        attributes {
                            attribute(org.gradle.api.attributes.Attribute.of("com.android.build.api.attributes.BuildTypeAttr", String::class.java), "debug")
                        }
                    })
            }
        }
    }

    extensions.configure<DetektExtension> {
        toolVersion = detektVersion
        buildUponDefaultConfig = false
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        parallel = true
        ignoreFailures = false
        autoCorrect = false
        basePath = rootProject.projectDir.absolutePath
    }

    tasks.withType<Detekt>().configureEach {
        setSource(files("src/main/kotlin", "src/main/java"))
        exclude("**/build/**", "**/generated/**")
        jvmTarget = "11"
        reports {
            xml.required.set(true)
            html.required.set(true)
            sarif.required.set(true)
            txt.required.set(true)
        }
    }
}

val detekt by tasks.registering {
    group = "verification"
    description = "Runs strict Detekt analysis for every Android module."
    dependsOn(subprojects.map { "${it.path}:detekt" })
}
