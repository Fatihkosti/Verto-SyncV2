package com.verto.app.feature.settings.domain.repository

import com.verto.app.utils.AppFontSize
import com.verto.app.utils.ThemeMode
import kotlinx.coroutines.flow.Flow

/** الحد الذي تحتاجه واجهة المظهر دون معرفة آلية التخزين المحلية. */
interface AppearanceSettingsGateway {
    val themeMode: Flow<ThemeMode>
    val appFontSize: Flow<AppFontSize>

    suspend fun setThemeMode(value: ThemeMode)
    suspend fun setAppFontSize(value: AppFontSize)
}
