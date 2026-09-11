package com.verto.app.core.session.domain

import kotlinx.coroutines.flow.Flow

/** تخزين محلي صغير لحالة App Shell دون كشف DataStore لطبقة العرض. */
interface DrawerSectionStateStore {
    val lastOpenSection: Flow<String>
    suspend fun setLastOpenSection(value: String)
}
