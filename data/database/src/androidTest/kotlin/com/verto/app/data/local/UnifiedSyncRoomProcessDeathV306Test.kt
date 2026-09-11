package com.verto.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/** Requires a real force-stop/two-process harness. Explicitly non-gating in the static-only Session 306. */
@RunWith(AndroidJUnit4::class)
class UnifiedSyncRoomProcessDeathV306Test {
    @Ignore("NOT_RUN_ENVIRONMENT_UNAVAILABLE: real process-death requires device/emulator")
    @Test
    fun real_process_death_preserves_unified_protocol_state() = Unit
}
