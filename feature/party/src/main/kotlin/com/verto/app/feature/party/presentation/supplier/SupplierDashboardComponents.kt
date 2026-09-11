package com.verto.app.feature.party.presentation.supplier

import com.verto.app.feature.party.presentation.shared.PartyContactActionButton

import com.verto.app.feature.party.presentation.shared.PartyDimensions
import com.verto.app.feature.party.presentation.shared.PartyTextScale

import com.verto.app.feature.party.domain.model.*
import com.verto.app.feature.party.application.model.*

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.ui.components.VertoTopBar
import com.verto.app.ui.components.WhatsAppIcon
import com.verto.app.feature.party.presentation.shared.DashboardInvoiceRow
import com.verto.app.ui.components.InfoChip
import com.verto.app.ui.components.KpiMini
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import com.verto.app.utils.WhatsAppUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.util.Calendar
import javax.inject.Inject

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun SupplierContactCard(
    supplier: PartyClient
) {
    // نجلب الـ context هنا لتشغيل الاتصال والواتساب مباشرة
    val context = LocalContext.current

    val secondaryPhones = supplier.secondaryPhones.toSecondaryPhoneList()
    val bankAccounts    = supplier.bankAccount.toBankAccountList()
    val hasPrimaryPhone = supplier.phone.isNotBlank()

    val supplierType = "مورد"

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PartyDimensions.dp16))
            .background(BgCard)
            .border(PartyDimensions.dp1, AccentBlue.copy(0.2f), RoundedCornerShape(PartyDimensions.dp16))
            .padding(PartyDimensions.dp16),
        verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp10)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // ── لا صورة — تم الحذف (نقطة 9) ─────────
            Column(Modifier.weight(1f)) {
                Text(supplier.name, color = TextPrimary, fontSize = PartyTextScale.sp16, fontWeight = FontWeight.Bold)
                if (hasPrimaryPhone) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp4)
                    ) {
                        Icon(WhatsAppIcon, null, tint = WhatsAppBrand, modifier = Modifier.size(PartyDimensions.dp12))
                        Text(supplier.phone, color = TextMuted, fontSize = PartyTextScale.sp13)
                    }
                }
                // ── صفة المورد الحقيقية بدل "عميل" (نقطة 8) ──
                Text(supplierType, color = AccentBlue, fontSize = PartyTextScale.sp11)
            }
            if (hasPrimaryPhone) {
                Row(horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp8)) {
                    // ── زر الاتصال — يفتح الهاتف مباشرة (نقطة 10) ──
                    PartyContactActionButton(
                        icon  = Icons.Filled.Phone,
                        color = SuccessColor,
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${supplier.phone}")
                            }
                            context.startActivity(intent)
                        }
                    )
                    // ── زر واتساب — يفتح واتساب مباشرة (نقطة 10) ──
                    PartyContactActionButton(
                        icon  = WhatsAppIcon,
                        color = WhatsAppBrand,
                        onClick = {
                            val phone = supplier.phone.trimStart('0').let {
                                if (!it.startsWith("+")) "20$it" else it
                            }
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://wa.me/$phone")
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }

        if (secondaryPhones.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp4)) {
                secondaryPhones.forEach { phone ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp6)
                    ) {
                        Icon(Icons.Filled.Phone, null, tint = TextMuted, modifier = Modifier.size(PartyDimensions.dp13))
                        Text(phone, color = TextSecondary, fontSize = PartyTextScale.sp13)
                    }
                }
            }
        }


    }
}
