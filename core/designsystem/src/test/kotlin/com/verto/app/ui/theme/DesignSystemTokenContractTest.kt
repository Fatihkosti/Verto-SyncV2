package com.verto.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSystemTokenContractTest {
    @Test
    fun motion_roles_are_monotonic_and_centralized() {
        assertEquals(0, VertoMotion.instant)
        assertEquals(120, VertoMotion.fast)
        assertEquals(220, VertoMotion.normal)
        assertEquals(360, VertoMotion.slow)
        assertEquals(500, VertoMotion.emphasized)
        assertEquals(800, VertoMotion.attentionPulse)
        assertEquals(1500, VertoMotion.long)
        assertTrue(VertoMotion.fast < VertoMotion.normal)
        assertTrue(VertoMotion.normal < VertoMotion.slow)
        assertTrue(VertoMotion.slow < VertoMotion.emphasized)
        assertTrue(VertoMotion.emphasized < VertoMotion.attentionPulse)
        assertTrue(VertoMotion.attentionPulse < VertoMotion.long)
    }

    @Test
    fun adaptive_breakpoints_are_ordered() {
        assertTrue(VertoAdaptiveTokens.compactMaxWidth < VertoAdaptiveTokens.mediumMaxWidth)
        assertTrue(VertoAdaptiveTokens.mediumMaxWidth < VertoAdaptiveTokens.expandedMinWidth)
    }

    @Test
    fun adaptive_inline_stack_contract_preserves_width_and_font_scale_boundaries() {
        assertTrue(VertoAdaptiveTokens.inlineStackThreshold < VertoAdaptiveTokens.compactMaxWidth)
        assertTrue(VertoAdaptiveTokens.shouldStackInlineContent(320.dp, 1.0f))
        assertTrue(VertoAdaptiveTokens.shouldStackInlineContent(359.dp, 1.0f))
        assertTrue(!VertoAdaptiveTokens.shouldStackInlineContent(360.dp, 1.0f))
        assertTrue(!VertoAdaptiveTokens.shouldStackInlineContent(412.dp, 1.0f))
        assertTrue(!VertoAdaptiveTokens.shouldStackInlineContent(412.dp, 1.49f))
        assertTrue(VertoAdaptiveTokens.shouldStackInlineContent(412.dp, 1.5f))
        assertTrue(VertoAdaptiveTokens.shouldStackInlineContent(412.dp, 2.0f))
    }

    @Test
    fun minimum_touch_target_is_at_least_wcag_recommended_size() {
        assertEquals(48f, VertoSize.minTouchTarget.value, 0.001f)
    }

    @Test
    fun semantic_danger_and_secondary_contract_is_theme_aware() {
        assertNotEquals(LightVertoColors.danger, DarkVertoColors.danger)
        assertNotEquals(LightVertoColors.dangerContainer, DarkVertoColors.dangerContainer)

        assertEquals(Color(0xFFB91C1C), LightVertoColors.danger)
        assertEquals(Color(0xFFFFEEEE), LightVertoColors.dangerContainer)
        assertEquals(Color.White, LightVertoColors.onDanger)
        assertEquals(Color(0xFF10111A), LightVertoColors.onSecondary)

        assertEquals(Color(0xFFF87171), DarkVertoColors.danger)
        assertEquals(Color(0xFF4A171B), DarkVertoColors.dangerContainer)
        assertEquals(Color(0xFF10111A), DarkVertoColors.onDanger)
        assertEquals(Color(0xFF10111A), DarkVertoColors.onSecondary)
    }
}
