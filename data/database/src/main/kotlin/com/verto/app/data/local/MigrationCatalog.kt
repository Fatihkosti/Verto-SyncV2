package com.verto.app.data.local

import androidx.room.migration.Migration

/** المصدر الوحيد لتسلسل Room المدعوم. */
const val ROOM_SCHEMA_VERSION: Int = 101

val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
    MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
    MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
    MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
    MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25,
    MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29,
    MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33,
    MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37,
    MIGRATION_37_38, MIGRATION_38_39, MIGRATION_39_40, MIGRATION_40_41,
    MIGRATION_41_42, MIGRATION_42_43, MIGRATION_43_44, MIGRATION_44_45, MIGRATION_45_46, MIGRATION_46_47, MIGRATION_47_48, MIGRATION_48_49, MIGRATION_49_50, MIGRATION_50_51, MIGRATION_51_52, MIGRATION_52_53, MIGRATION_53_54, MIGRATION_54_55, MIGRATION_55_56, MIGRATION_56_57, MIGRATION_57_58, MIGRATION_58_59, MIGRATION_59_60, MIGRATION_60_61, MIGRATION_61_62, MIGRATION_62_63, MIGRATION_63_64, MIGRATION_64_65, MIGRATION_65_66, MIGRATION_66_67, MIGRATION_67_68, MIGRATION_68_69, MIGRATION_69_70, MIGRATION_70_71, MIGRATION_71_72, MIGRATION_72_73, MIGRATION_73_74, MIGRATION_74_75, MIGRATION_75_76, MIGRATION_76_77, MIGRATION_77_78, MIGRATION_78_79, MIGRATION_79_80, MIGRATION_80_81, MIGRATION_81_82, MIGRATION_82_83, MIGRATION_83_84, MIGRATION_84_85, MIGRATION_85_86, MIGRATION_86_87, MIGRATION_87_88, MIGRATION_88_89, MIGRATION_89_90, MIGRATION_90_91, MIGRATION_91_92, MIGRATION_92_93, MIGRATION_93_94, MIGRATION_94_95, MIGRATION_95_96, MIGRATION_96_97, MIGRATION_97_98, MIGRATION_98_99, MIGRATION_99_100, MIGRATION_100_101
)

/** يعيد المسار المتصل فقط، ويفشل مغلقًا إذا ظهرت فجوة أو تكرار. */
fun migrationPath(fromVersion: Int, toVersion: Int = ROOM_SCHEMA_VERSION): List<Migration> {
    require(fromVersion in 1..toVersion) { "Unsupported start schema: $fromVersion" }
    require(toVersion <= ROOM_SCHEMA_VERSION) { "Unsupported target schema: $toVersion" }
    if (fromVersion == toVersion) return emptyList()

    val byStart = ALL_MIGRATIONS.groupBy { it.startVersion }
    val path = mutableListOf<Migration>()
    var current = fromVersion
    while (current < toVersion) {
        val candidates = byStart[current].orEmpty()
        require(candidates.size == 1) { "Migration catalog gap or duplicate at schema $current" }
        val migration = candidates.single()
        require(migration.endVersion == current + 1) {
            "Non-consecutive migration ${migration.startVersion}->${migration.endVersion}"
        }
        path += migration
        current = migration.endVersion
    }
    return path
}
