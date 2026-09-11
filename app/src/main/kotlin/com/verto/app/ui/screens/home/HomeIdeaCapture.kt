package com.verto.app.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.ui.components.VertoPrimaryButton
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.VertoSpacing
import com.verto.feature.dashboard.api.TeamObservationCategory
import kotlinx.coroutines.delay

private data class CaptureCategorySpec(
    val category: TeamObservationCategory,
    val label: String,
    val title: String,
    val supportingText: String,
)

private val captureCategories = listOf(
    CaptureCategorySpec(
        category = TeamObservationCategory.IDEA,
        label = "فكرة",
        title = "عندك فكرة تستحق التجربة؟",
        supportingText = "أي تغيير صغير قد يختصر وقتًا أو يحسن طريقة العمل. اكتب الفكرة كما هي.",
    ),
    CaptureCategorySpec(
        category = TeamObservationCategory.MARKET_INFO,
        label = "معلومة سوق",
        title = "شنو الجديد في السوق؟",
        supportingText = "سعر، صنف مطلوب، حركة منافس أو معلومة سمعتها من عميل—سجلها قبل أن تضيع.",
    ),
    CaptureCategorySpec(
        category = TeamObservationCategory.COMPLAINT,
        label = "شكوى",
        title = "في شكوى لازم نعرفها؟",
        supportingText = "اكتب ما حدث باختصار كما قاله العميل أو كما شاهدته أنت.",
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeIdeaCaptureCard(
    text: String,
    isSubmitting: Boolean,
    onTextChange: (String) -> Unit,
    onSubmit: (TeamObservationCategory) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { captureCategories.size })

    // Rotate only while idle. Once the user starts typing, the selected category remains stable.
    LaunchedEffect(pagerState.currentPage, text.isBlank()) {
        if (!text.isBlank()) return@LaunchedEffect
        delay(CAPTURE_ROTATION_INTERVAL_MS)
        val nextPage = (pagerState.currentPage + 1) % captureCategories.size
        pagerState.animateScrollToPage(nextPage)
    }

    VertoCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(VertoSpacing.none),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().height(286.dp),
                pageSpacing = VertoSpacing.sm,
            ) { page ->
                val spec = captureCategories[page]
                Column(
                    modifier = Modifier.fillMaxWidth().padding(VertoSpacing.md),
                ) {
                    Text(
                        text = spec.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = AccentPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(AccentPrimary.copy(alpha = 0.10f))
                            .padding(horizontal = VertoSpacing.sm, vertical = VertoSpacing.xs),
                    )
                    Spacer(Modifier.height(VertoSpacing.sm))
                    Text(
                        text = spec.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(VertoSpacing.xs))
                    Text(
                        text = spec.supportingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(VertoSpacing.sm))
                    VertoOutlinedTextField(
                        value = text,
                        onValueChange = { next -> onTextChange(next.take(MAX_OBSERVATION_LENGTH)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 4,
                    )
                }
            }

            VertoPrimaryButton(
                text = "إرسال",
                onClick = { onSubmit(captureCategories[pagerState.currentPage].category) },
                enabled = !isSubmitting,
                isLoading = isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VertoSpacing.md),
            )
            Spacer(Modifier.height(VertoSpacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = VertoSpacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                captureCategories.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) AccentPrimary
                                else BorderColor,
                            ),
                    )
                }
            }
        }
    }
}

private const val MAX_OBSERVATION_LENGTH: Int = 1200
private const val CAPTURE_ROTATION_INTERVAL_MS: Long = 5L * 60L * 1000L
