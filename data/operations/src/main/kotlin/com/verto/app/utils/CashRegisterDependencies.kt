package com.verto.app.utils

import javax.inject.Inject
import javax.inject.Singleton

/** Session 335 keeps CashRegisterManager's historical constructor width stable. */
@Singleton
class CashRegisterDependencies @Inject constructor(
    val prefs: PreferencesManager,
    internal val writer: CashMovementSyncWriter,
)
