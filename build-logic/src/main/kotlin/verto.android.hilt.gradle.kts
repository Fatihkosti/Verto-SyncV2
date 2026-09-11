import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("implementation", libs.findLibrary("hilt-android").get())
    add("ksp", libs.findLibrary("hilt-android-compiler").get())
}

// Hilt's KSP processor emits per-round Java copies that AGP 8.7 also scans.
// Remove that diagnostic subtree after successful generation so Javac sees
// only the canonical output under generated/ksp/<variant>/java.
tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }.configureEach {
    doLast {
        val variant = name.removePrefix("ksp").removeSuffix("Kotlin")
            .replaceFirstChar(Char::lowercaseChar)
        delete(layout.buildDirectory.dir("generated/ksp/$variant/java/byRounds"))
    }
}
