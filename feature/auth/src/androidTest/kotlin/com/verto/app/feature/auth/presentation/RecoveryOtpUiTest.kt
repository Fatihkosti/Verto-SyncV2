package com.verto.app.feature.auth.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecoveryOtpUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun otp_input_renders_eight_editable_cells() {
        composeRule.setContent {
            MaterialTheme {
                OtpCodeInput(code = "", onCodeChange = {}, onComplete = {})
            }
        }

        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(8)
    }

    @Test fun full_eight_digit_paste_populates_code_and_completes_once() {
        var code by mutableStateOf("")
        var completed: String? = null
        var completionCount = 0

        composeRule.setContent {
            MaterialTheme {
                OtpCodeInput(
                    code = code,
                    onCodeChange = { code = it },
                    onComplete = {
                        completed = it
                        completionCount += 1
                    },
                )
            }
        }

        composeRule.onAllNodes(hasSetTextAction())[0].performTextReplacement("12345678")
        composeRule.waitForIdle()

        assertEquals("12345678", code)
        assertEquals("12345678", completed)
        assertEquals(1, completionCount)
    }

    @Test fun six_digit_legacy_paste_does_not_complete() {
        var code by mutableStateOf("")
        var completionCount = 0

        composeRule.setContent {
            MaterialTheme {
                OtpCodeInput(
                    code = code,
                    onCodeChange = { code = it },
                    onComplete = { completionCount += 1 },
                )
            }
        }

        composeRule.onAllNodes(hasSetTextAction())[0].performTextReplacement("123456")
        composeRule.waitForIdle()

        assertEquals("123456", code)
        assertEquals(0, completionCount)
    }
}
