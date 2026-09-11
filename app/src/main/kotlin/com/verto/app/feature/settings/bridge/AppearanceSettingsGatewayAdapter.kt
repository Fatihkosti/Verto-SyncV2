package com.verto.app.feature.settings.bridge
import com.verto.app.feature.settings.domain.repository.AppearanceSettingsGateway
import com.verto.app.utils.AppFontSize
import com.verto.app.utils.PreferencesManager
import com.verto.app.utils.ThemeMode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** محول انتقالي يبقي DataStore الحالي خلف عقد إعدادات المظهر. */
@Singleton
class AppearanceSettingsGatewayAdapter @Inject constructor(
    private val preferences: PreferencesManager
) : AppearanceSettingsGateway {
    override val themeMode: Flow<ThemeMode> = preferences.themeMode
    override val appFontSize: Flow<AppFontSize> = preferences.appFontSize

    override suspend fun setThemeMode(value: ThemeMode) {
        preferences.setThemeMode(value)
    }

    override suspend fun setAppFontSize(value: AppFontSize) {
        preferences.setAppFontSize(value)
    }
}
