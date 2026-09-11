package com.verto.app.ui.screens.onboarding

import androidx.compose.ui.res.stringResource

import com.verto.app.R

import com.verto.app.ui.components.VertoButton

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.verto.app.ui.theme.*
import kotlinx.coroutines.launch

data class OnboardingPage(val title: String, val description: String, val icon: ImageVector)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = listOf(
        OnboardingPage("أموالك تحت السيطرة", "نظم ديونك، تابع مستحقاتك، وحصّل أموالك أسرع مع Verto.", Icons.Filled.AccountBalanceWallet),
        OnboardingPage("راقب كل قرش", "سجل مصروفاتك لحظة بلحظة واعرف فلوسك رايحة فين بضغطة زر.", Icons.Filled.Analytics),
        OnboardingPage("بياناتك في خزنة", "احتفظ بنسخة احتياطية لملفاتك واسترجعها في أي وقت، أمانك مضمون.", Icons.Filled.CloudUpload),
        OnboardingPage("أسرار عملك في أمان", "احمِ حساباتك برمز قفل خاص لا يعرفه أحد غيرك.", Icons.Filled.Lock)
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(containerColor = BgDeep) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(OnboardingDimensions.dp20),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // زر تخطي فوق
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onFinish) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ds_d467da0e5d11), color = TextMuted, fontSize = OnboardingTextScale.sp14)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // محتوى الشاشات المتحرك
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { position ->
                val page = pages[position]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(OnboardingDimensions.dp120).clip(CircleShape).background(BgCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(page.icon, null, tint = AccentPrimary, modifier = Modifier.size(OnboardingDimensions.dp60))
                    }
                    Spacer(modifier = Modifier.height(OnboardingDimensions.dp32))
                    Text(page.title, color = TextPrimary, fontSize = OnboardingTextScale.sp24, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(OnboardingDimensions.dp16))
                    Text(page.description, color = TextSecondary, fontSize = OnboardingTextScale.sp15, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = OnboardingDimensions.dp20))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // مؤشر النقاط (Dots)
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                repeat(pages.size) { iteration ->
                    val color = if (pagerState.currentPage == iteration) AccentPrimary else BgCard
                    val width = if (pagerState.currentPage == iteration) OnboardingDimensions.dp24 else OnboardingDimensions.dp10
                    Box(
                        modifier = Modifier.padding(OnboardingDimensions.dp3).clip(CircleShape).background(color).height(OnboardingDimensions.dp8).width(width)
                    )
                }
            }

            Spacer(modifier = Modifier.height(OnboardingDimensions.dp32))

            // زر المتابعة
            VertoButton(
                onClick = {
                    if (pagerState.currentPage < pages.size - 1) {
                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onFinish()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(OnboardingDimensions.dp56),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(OnboardingDimensions.dp16)
            ) {
                Text(if (pagerState.currentPage == pages.size - 1) stringResource(R.string.legacy_ui_fd33d34ebb92) else stringResource(R.string.legacy_ui_afa7f5217f26), color = TextPrimary, fontSize = OnboardingTextScale.sp16, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(OnboardingDimensions.dp20))
        }
    }
}
