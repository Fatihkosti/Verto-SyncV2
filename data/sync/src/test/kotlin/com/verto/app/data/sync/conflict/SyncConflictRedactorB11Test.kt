package com.verto.app.data.sync.conflict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncConflictRedactorB11Test {
    @Test
    fun `redacts secrets before presenting local and remote payloads`() {
        val redacted = SyncConflictRedactor.preview(
            SyncConflictRedactor.redact("""{"name":"A","token":"top-secret","nested":{"otp":"123456"}}""")
        )
        assertTrue(redacted.contains("[REDACTED]"))
        assertFalse(redacted.contains("top-secret"))
        assertFalse(redacted.contains("123456"))
    }

    @Test
    fun `reports deterministic differing field paths`() {
        val local = SyncConflictRedactor.redact("""{"name":"A","qty":1,"nested":{"x":4}}""")
        val remote = SyncConflictRedactor.redact("""{"name":"B","qty":1,"nested":{"x":7}}""")
        val diffs = SyncConflictRedactor.diff(local, remote)
        assertEquals(listOf("$.name", "$.nested.x"), diffs.map { it.path })
    }
}
