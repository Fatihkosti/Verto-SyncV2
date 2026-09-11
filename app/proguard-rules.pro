# Verto release rules — v45
# Keep this file narrow. AndroidX Room, Hilt/Dagger, Firebase, Ktor and Supabase
# publish their own consumer rules and must not be preserved package-wide here.

# Generic metadata required by serializers that inspect generic signatures or annotations.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# kotlinx.serialization: preserve only named companion serializer entry points.
# Generated serializers remain shrinkable and obfuscatable.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers,allowoptimization,allowshrinking,allowobfuscation class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers,allowoptimization,allowshrinking,allowobfuscation class <1>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor's optional SLF4J bridge is absent on Android; suppress only that optional warning.
-dontwarn org.slf4j.**
