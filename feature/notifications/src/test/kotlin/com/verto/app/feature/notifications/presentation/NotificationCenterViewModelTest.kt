package com.verto.app.feature.notifications.presentation

import com.verto.app.feature.notifications.domain.model.AppNotification
import com.verto.app.feature.notifications.domain.model.AppNotificationAudience
import com.verto.app.feature.notifications.domain.repository.NotificationCenterGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationCenterViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `notifications flow updates ui state`() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val vm = NotificationCenterViewModel(gateway)
        gateway.items.value = listOf(note("1"))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertEquals("1", vm.uiState.value.notifications.single().id)
    }

    @Test fun `sync never marks notifications as read`() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val vm = NotificationCenterViewModel(gateway)
        advanceUntilIdle()
        vm.syncNow()
        advanceUntilIdle()
        assertEquals(0, gateway.markAllCount)
    }

    @Test fun `failed explicit mark all exposes error`() = runTest(dispatcher) {
        val gateway = FakeGateway().apply { markAllFailure = IllegalStateException("offline") }
        val vm = NotificationCenterViewModel(gateway)
        advanceUntilIdle()
        vm.markAllAsRead()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.error)
    }

    private fun note(id: String) = AppNotification(
        id,
        AppNotificationAudience.DIRECT_EMPLOYEE,
        "t",
        "b",
        null,
        false,
        1,
    )

    private class FakeGateway : NotificationCenterGateway {
        val items = MutableStateFlow<List<AppNotification>>(emptyList())
        var syncResult: Result<Unit> = Result.success(Unit)
        var markAllCount = 0
        var markAllFailure: Throwable? = null
        override suspend fun observeCurrentUserNotifications(): Flow<List<AppNotification>> = items
        override suspend fun markAsRead(id: String) = Unit
        override suspend fun markAllAsRead() {
            markAllFailure?.let { throw it }
            markAllCount++
        }
        override suspend fun sync() = syncResult
    }
}
