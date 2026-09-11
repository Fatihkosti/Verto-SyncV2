package com.verto.app.feature.settings.presentation.appearance

import com.verto.app.feature.settings.domain.repository.AppearanceSettingsGateway
import com.verto.app.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceSettingsViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun setup(){Dispatchers.setMain(dispatcher)}
    @After fun tearDown(){Dispatchers.resetMain()}
    @Test fun `events persist requested appearance values`() = runTest(dispatcher) {
        val gateway=FakeGateway(); val vm=AppearanceSettingsViewModel(gateway)
        vm.setThemeMode(ThemeMode.DARK); vm.setAppFontSize(AppFontSize.LARGE); advanceUntilIdle()
        assertEquals(ThemeMode.DARK,gateway.theme.value); assertEquals(AppFontSize.LARGE,gateway.font.value)
    }
    private class FakeGateway:AppearanceSettingsGateway { val theme=MutableStateFlow(ThemeMode.AUTO); val font=MutableStateFlow(AppFontSize.MEDIUM); override val themeMode:Flow<ThemeMode> = theme; override val appFontSize:Flow<AppFontSize> = font; override suspend fun setThemeMode(value:ThemeMode){theme.value=value}; override suspend fun setAppFontSize(value:AppFontSize){font.value=value} }
}
