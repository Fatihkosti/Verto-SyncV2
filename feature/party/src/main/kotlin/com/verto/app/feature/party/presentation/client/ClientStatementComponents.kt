package com.verto.app.feature.party.presentation.client

import com.verto.app.feature.party.presentation.shared.PartyDimensions
import com.verto.app.feature.party.presentation.shared.PartyTextScale

import com.verto.app.feature.party.domain.model.*
import com.verto.app.feature.party.application.model.*

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.ui.components.VertoTopBar
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import com.verto.app.utils.WhatsAppUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.util.Calendar
import javax.inject.Inject

// ─────────────────────────────────────────────────────
// طباعة الكشف عبر WebView
// ─────────────────────────────────────────────────────
internal fun printClientStatement(
    context       : Context,
    clientName    : String,
    filterFrom    : Long,
    filterTo      : Long,
    openingBalance: Double,
    rows          : List<ClientStatementRow>
) {
    val fromStr = DateUtils.formatDateEn(filterFrom)
    val toStr   = DateUtils.formatDateEn(filterTo)

    val openingHtml = if (openingBalance != 0.0) {
        val color = if (openingBalance > 0) "#e53935" else "#43a047"
        val label = if (openingBalance > 0) "رصيد مُرحَّل (مطلوب من العميل)" else "رصيد لصالح العميل"
        """
        <tr style="background:#fff3e0;">
          <td colspan="4" style="padding:8px;font-weight:bold;color:#e65100;">$label حتى $fromStr</td>
          <td style="padding:8px;text-align:left;font-weight:bold;color:$color;">
            ${String.format("%.2f", kotlin.math.abs(openingBalance))}
          </td>
        </tr>
        """.trimIndent()
    } else ""

    val rowsHtml = rows.joinToString("") { r ->
        val balColor = if (r.balance > 0) "#e53935" else "#43a047"
        """
        <tr>
          <td style="padding:6px;">${DateUtils.formatDateEn(r.date)}</td>
          <td style="padding:6px;">${r.description}</td>
          <td style="padding:6px;text-align:left;color:#e53935;">
            ${if (r.debit  > 0) String.format("%.2f", r.debit)  else "—"}
          </td>
          <td style="padding:6px;text-align:left;color:#43a047;">
            ${if (r.credit > 0) String.format("%.2f", r.credit) else "—"}
          </td>
          <td style="padding:6px;text-align:left;font-weight:bold;color:$balColor;">
            ${String.format("%.2f", kotlin.math.abs(r.balance))}
          </td>
        </tr>
        """.trimIndent()
    }

    val closingBalance = rows.lastOrNull()?.balance ?: openingBalance
    val closingColor   = if (closingBalance >= 0) "#e53935" else "#43a047"
    val closingLabel   = if (closingBalance >= 0) "إجمالي المطلوب من العميل" else "رصيد لصالح العميل"

    val html = """
    <!DOCTYPE html><html dir="rtl"><head>
    <meta charset="UTF-8"/>
    <style>
      body { font-family: Arial, sans-serif; font-size: 12px; margin: 20px; }
      h2   { text-align: center; color: #5c6bc0; }
      p    { text-align: center; color: #555; margin: 4px 0; }
      table{ width: 100%; border-collapse: collapse; margin-top: 16px; }
      th   { background: #5c6bc0; color: #fff; padding: 8px; }
      tr:nth-child(even) { background: #f5f5f5; }
      .closing { font-weight: bold; font-size: 14px; text-align: center; margin-top: 16px; }
    </style>
    </head><body>
    <h2>كشف حساب — $clientName</h2>
    <p>الفترة من: $fromStr &nbsp;|&nbsp; إلى: $toStr</p>
    <table>
      <tr><th>التاريخ</th><th>البيان</th><th>مدين</th><th>دائن</th><th>الرصيد</th></tr>
      $openingHtml
      $rowsHtml
    </table>
    <p class="closing" style="color:$closingColor;">
      $closingLabel: ${String.format("%.2f", kotlin.math.abs(closingBalance))}
    </p>
    </body></html>
    """.trimIndent()

    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val printAdapter = webView.createPrintDocumentAdapter("كشف حساب - $clientName")
            printManager.print(
                "كشف_حساب_${clientName}_${fromStr}_${toStr}",
                printAdapter,
                PrintAttributes.Builder().build()
            )
        }
    }
    webView.loadData(html, "text/html; charset=UTF-8", "UTF-8")
}
