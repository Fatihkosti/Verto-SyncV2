package com.verto.app.ui.components

import com.verto.app.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.verto.app.data.model.EmployeePermissions
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.data.remote.RoleProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * حارس الصلاحيات للواجهة (الجلسة 3) — طبقة دفاع UI/Route.
 * يقرأ صلاحيات المستخدم الحالي ويعرض المحتوى فقط لمن يملك الصلاحية.
 *
 * هذه طبقة UX؛ المنع الحقيقي في طبقة UseCase (SaveInvoice/AddPayment/Expense)
 * وسيرفرياً عبر RLS. admin يمرّ دائماً (`fullAccess`).
 */
@HiltViewModel
class PermissionGateViewModel @Inject constructor(
    permissionProvider: PermissionProvider
) : ViewModel() {
    val permissions: StateFlow<EmployeePermissions?> = permissionProvider.permissions
}

@HiltViewModel
class AdminGateViewModel @Inject constructor(
    roleProvider: RoleProvider
) : ViewModel() {
    val isAdmin: StateFlow<Boolean?> = roleProvider.role
        .map { if (it == null) null else it == "admin" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

/**
 * حارس دور الأدمن — يعرض [content] للمدير فقط، وإلا رسالة منع + رجوع.
 * أثناء تحميل الدور (null) يعرض مؤشّر انتظار.
 */
@Composable
fun AdminGate(
    deniedMessage: String = "هذه الشاشة متاحة للمدير فقط",
    onDenied: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val vm: AdminGateViewModel = hiltViewModel()
    val isAdmin by vm.isAdmin.collectAsStateWithLifecycle()

    when (isAdmin) {
        null -> Box(
            Modifier.fillMaxSize().padding(AppChromeDimensions.dp24),
            contentAlignment = Alignment.Center,
        ) {
            VertoLoadingState(message = androidx.compose.ui.res.stringResource(R.string.ds_74886f528494))
        }
        true -> content()
        false -> {
            androidx.compose.runtime.LaunchedEffect(Unit) { onDenied?.invoke() }
            Box(
                modifier = Modifier.fillMaxSize().padding(AppChromeDimensions.dp24),
                contentAlignment = Alignment.Center,
            ) {
                VertoStatusBanner(
                    title = androidx.compose.ui.res.stringResource(R.string.ds_acd3fb38fb41),
                    message = deniedMessage,
                    tone = VertoStatusTone.Permission,
                )
            }
        }
    }
}

/**
 * يعرض [content] إن كانت [check] محققة لصلاحيات المستخدم الحالي، وإلا رسالة منع.
 * أثناء تحميل الصلاحيات (null) يعرض مؤشّر انتظار بدل وميض «ممنوع».
 *
 * @param check دالة على [EmployeePermissions] ترجع true إذا كان مسموحاً.
 * @param onDenied يُستدعى مرة عند اكتشاف المنع (مثلاً للرجوع للخلف) — اختياري.
 */
@Composable
fun PermissionGate(
    check: (EmployeePermissions) -> Boolean,
    deniedMessage: String = "لا تملك صلاحية الوصول لهذه الشاشة",
    onDenied: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val vm: PermissionGateViewModel = hiltViewModel()
    val perms by vm.permissions.collectAsStateWithLifecycle()

    when {
        perms == null -> Box(
            Modifier.fillMaxSize().padding(AppChromeDimensions.dp24),
            contentAlignment = Alignment.Center,
        ) {
            VertoLoadingState(message = androidx.compose.ui.res.stringResource(R.string.ds_74886f528494))
        }
        check(checkNotNull(perms)) -> content()
        else -> {
            androidx.compose.runtime.LaunchedEffect(Unit) { onDenied?.invoke() }
            Box(
                modifier = Modifier.fillMaxSize().padding(AppChromeDimensions.dp24),
                contentAlignment = Alignment.Center,
            ) {
                VertoStatusBanner(
                    title = androidx.compose.ui.res.stringResource(R.string.ds_acd3fb38fb41),
                    message = deniedMessage,
                    tone = VertoStatusTone.Permission,
                )
            }
        }
    }
}
