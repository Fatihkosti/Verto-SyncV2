package com.verto.app.feature.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.data.sync.PersistedSyncReport
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.sync.conflict.SyncConflictReviewRecord
import com.verto.app.data.sync.conflict.SyncConflictReviewService
import com.verto.app.data.sync.conflict.SyncConflictResolutionException
import com.verto.app.data.sync.SyncOperationOutcome
import com.verto.app.data.sync.SyncCoordinatorPhase
import com.verto.app.data.sync.SyncOperations
import com.verto.app.data.sync.SyncReportFailureCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncOperations: SyncOperations,
    private val conflictReviewService: SyncConflictReviewService,
    private val sessionReader: SessionReader,
) : ViewModel() {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState = _syncState.asStateFlow()

    private val _conflictReviewState = MutableStateFlow(SyncConflictReviewUiState())
    val conflictReviewState: StateFlow<SyncConflictReviewUiState> = _conflictReviewState.asStateFlow()

    val drawerSyncState: StateFlow<DrawerSyncUiState> = combine(
        syncOperations.lastReport,
        syncOperations.isSyncInProgress,
    ) { report, inProgress ->
        report.toDrawerState(inProgress)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DrawerSyncUiState(),
    )

    init {
        viewModelScope.launch { syncOperations.restoreLastReport() }
    }

    fun push() {
        if (drawerSyncState.value.inProgress) return
        viewModelScope.launch {
            _syncState.value = SyncState.Loading("جاري المزامنة...")
            syncOperations.requestSync()
                .onSuccess {
                    _syncState.value = SyncState.Success("تم تسجيل طلب المزامنة")
                    syncOperations.startRealtimeForCurrentProfile()
                }
                .onFailure { e ->
                    android.util.Log.e("SyncViewModel", "Sync request failed", e)
                    _syncState.value = SyncState.Error(e.toUserMessage())
                }
        }
    }

    fun pull() {
        viewModelScope.launch {
            _syncState.value = SyncState.Loading("جاري جلب البيانات...")
            val profileReady = kotlinx.coroutines.withTimeoutOrNull(5_000L) {
                while (!syncOperations.isProfileReady()) { kotlinx.coroutines.delay(200L) }
                true
            }
            if (profileReady == null) {
                _syncState.value = SyncState.Error("ملف الحساب غير جاهز للمزامنة. أعد المحاولة بعد اكتمال تحميل الحساب")
                return@launch
            }
            syncOperations.requestSync()
                .onSuccess {
                    _syncState.value = SyncState.Success("تم تسجيل طلب جلب البيانات")
                    syncOperations.startRealtimeForCurrentProfile()
                }
                .onFailure { e ->
                    android.util.Log.e("SyncViewModel", "Pull request failed", e)
                    _syncState.value = SyncState.Error(e.toUserMessage())
                }
        }
    }

    fun refreshConflictReviews() {
        viewModelScope.launch {
            _conflictReviewState.value = _conflictReviewState.value.copy(loading = true, errorMessage = null)
            runCatching {
                val organizationId = sessionReader.snapshot().organization.id
                require(organizationId.isNotBlank()) { "AUTH_REQUIRED" }
                conflictReviewService.listForOrganization(organizationId)
            }.onSuccess { rows ->
                _conflictReviewState.value = SyncConflictReviewUiState(items = rows)
            }.onFailure { error ->
                _conflictReviewState.value = _conflictReviewState.value.copy(
                    loading = false, errorMessage = error.toConflictUserMessage(),
                )
            }
        }
    }

    fun acceptServerConflict(conflictId: String, displayedServerVersion: Long) {
        resolveConflict(conflictId) {
            conflictReviewService.acceptServer(conflictId, displayedServerVersion)
        }
    }

    fun resendLocalConflict(conflictId: String, displayedServerVersion: Long) {
        resolveConflict(conflictId) {
            val result = conflictReviewService.resendLocal(conflictId, displayedServerVersion)
            // Decision is already durable; a scheduling failure must not misreport it as rolled back.
            syncOperations.requestSync()
            result
        }
    }

    private fun resolveConflict(conflictId: String, block: suspend () -> Any) {
        if (_conflictReviewState.value.resolvingConflictId != null) return
        viewModelScope.launch {
            _conflictReviewState.value = _conflictReviewState.value.copy(
                resolvingConflictId = conflictId, errorMessage = null,
            )
            runCatching { block() }
                .onSuccess { refreshConflictReviews() }
                .onFailure { error ->
                    _conflictReviewState.value = _conflictReviewState.value.copy(
                        resolvingConflictId = null, errorMessage = error.toConflictUserMessage(),
                    )
                }
        }
    }

    private fun Throwable.toConflictUserMessage(): String {
        val code = (this as? SyncConflictResolutionException)?.code ?: message.orEmpty()
        return when {
            code.contains("PERMISSION_DENIED") -> "لا تملك صلاحية حسم تعارض المزامنة"
            code.contains("OUTCOME_UNKNOWN") -> "مصير الطلب السابق غير مثبت؛ لا يمكن إسقاط النسخة المحلية"
            code.contains("REMOTE_VERSION_CHANGED") -> "تغيّرت نسخة الخادم؛ حدّث التعارض قبل اتخاذ القرار"
            code.contains("DOMAIN_CORRECTION_REQUIRED") -> "هذه حقيقة غير قابلة للاستبدال؛ استخدم مسار التصحيح/العكس المعتمد"
            code.contains("STATE_CHANGED") -> "تغيّرت حالة التعارض؛ أعد فتح المراجعة"
            else -> com.verto.app.utils.ErrorHumanizer.humanize(this)
        }
    }

    override fun onCleared() {
        super.onCleared()
        syncOperations.stopRealtime()
    }

    private fun Throwable.toUserMessage(): String =
        com.verto.app.utils.ErrorHumanizer.humanize(this)

    fun resetState() { _syncState.value = SyncState.Idle }
}

data class SyncConflictReviewUiState(
    val loading: Boolean = false,
    val items: List<SyncConflictReviewRecord> = emptyList(),
    val resolvingConflictId: String? = null,
    val errorMessage: String? = null,
)

data class DrawerSyncOperationUi(
    val label: String,
    val outcome: SyncOperationOutcome,
)

data class DrawerSyncDomainUi(
    val key: String,
    val label: String,
    val succeeded: List<DrawerSyncOperationUi>,
    val failed: List<DrawerSyncOperationUi>,
    val skipped: List<DrawerSyncOperationUi>,
)

data class DrawerSyncUiState(
    val inProgress: Boolean = false,
    val attemptedAtMillis: Long? = null,
    val lastSuccessfulAtMillis: Long? = null,
    val organizationLabel: String = "",
    val resumed: Boolean = false,
    val completed: Boolean? = null,
    val failureMessage: String? = null,
    val phase: SyncCoordinatorPhase = SyncCoordinatorPhase.IDLE,
    val pendingCount: Long = 0L,
    val requiresReviewCount: Long = 0L,
    val rejectedCount: Long = 0L,
    val domains: List<DrawerSyncDomainUi> = emptyList(),
) {
    val summary: String
        get() = when {
            inProgress || phase == SyncCoordinatorPhase.RUNNING -> "جاري المزامنة"
            phase == SyncCoordinatorPhase.REQUESTED -> "طلب المزامنة مسجّل"
            phase == SyncCoordinatorPhase.CONTINUATION_PENDING -> "المزامنة معلّقة وستُستأنف"
            phase == SyncCoordinatorPhase.NEEDS_REVIEW -> "توجد بيانات تحتاج مراجعة"
            phase == SyncCoordinatorPhase.AUTH_BLOCKED -> "المزامنة متوقفة بسبب الجلسة"
            phase == SyncCoordinatorPhase.FAILED -> "تعذرت المزامنة"
            completed == true || phase == SyncCoordinatorPhase.COMPLETED -> "آخر مزامنة: ${formatDateTime(lastSuccessfulAtMillis)}"
            attemptedAtMillis == null -> "لم تتم المزامنة بعد"
            domains.any { it.failed.isNotEmpty() } -> "اكتملت مع أخطاء"
            else -> "المزامنة بانتظار التنفيذ"
        }
}

internal fun PersistedSyncReport?.toDrawerState(inProgress: Boolean): DrawerSyncUiState {
    if (this == null) return DrawerSyncUiState(inProgress = inProgress)
    val grouped = operations.groupBy { domainFor(it.participantKey) }
        .map { (domain, operations) ->
            val values = operations.map {
                DrawerSyncOperationUi(
                    label = humanizeOperation(it.label),
                    outcome = it.outcome,
                )
            }
            DrawerSyncDomainUi(
                key = domain.first,
                label = domain.second,
                succeeded = values.filter { it.outcome == SyncOperationOutcome.SUCCEEDED },
                failed = values.filter {
                    it.outcome == SyncOperationOutcome.FAILED_COLLECTED ||
                        it.outcome == SyncOperationOutcome.FAILED_ABORTED
                },
                skipped = values.filter { it.outcome == SyncOperationOutcome.SKIPPED_CHECKPOINT },
            )
        }
        .sortedBy { DOMAIN_ORDER.indexOf(it.key).let { index -> if (index < 0) Int.MAX_VALUE else index } }
    return DrawerSyncUiState(
        inProgress = inProgress,
        attemptedAtMillis = attemptedAtMillis,
        lastSuccessfulAtMillis = lastSuccessfulAtMillis,
        organizationLabel = organizationLabel,
        resumed = resumed,
        completed = completed,
        failureMessage = failureCategory.toHumanMessage(),
        phase = coordinatorPhase,
        pendingCount = pendingCount,
        requiresReviewCount = requiresReviewCount,
        rejectedCount = rejectedCount,
        domains = grouped,
    )
}

private fun domainFor(participantKey: String): Pair<String, String> = when (participantKey) {
    "clients" -> "parties" to "العملاء والموردون"
    "invoices", "cash" -> "sales_finance" to "المبيعات والمالية"
    "inventory" -> "inventory" to "المخزون"
    "shipments" -> "logistics" to "اللوجستيات"
    "organization", "educational_content" -> "organization" to "المؤسسة"
    "optimal_outbox" -> "optimal" to "Optimal"
    else -> "other" to "بيانات أخرى"
}

private fun humanizeOperation(label: String): String = label
    .replace("push ", "رفع ", ignoreCase = true)
    .replace("pull ", "تنزيل ", ignoreCase = true)
    .trim()

private fun SyncReportFailureCategory?.toHumanMessage(): String? = when (this) {
    SyncReportFailureCategory.NETWORK -> "تعذّر الاتصال بخدمة المزامنة"
    SyncReportFailureCategory.AUTHENTICATION -> "انتهت صلاحية الجلسة"
    SyncReportFailureCategory.PERMISSION -> "لا تملك صلاحية مزامنة هذه البيانات"
    SyncReportFailureCategory.RATE_LIMITED -> "الخدمة تستقبل طلبات كثيرة حاليًا. انتظر قليلًا ثم أعد المحاولة"
    SyncReportFailureCategory.SERVER -> "خدمة المزامنة غير متاحة حاليًا"
    SyncReportFailureCategory.VALIDATION -> "تعذّر قبول بعض بيانات المزامنة"
    SyncReportFailureCategory.CONTRACT -> "تعذّر تنفيذ المزامنة بسبب عدم توافق الطلب مع الخدمة"
    SyncReportFailureCategory.CONFLICT -> "توجد بيانات متعارضة تحتاج مراجعة قبل اكتمال المزامنة"
    SyncReportFailureCategory.STALE_SCOPE -> "تغيّر الحساب أو المؤسسة أثناء المزامنة. أعد فتح الشاشة ثم حاول مرة أخرى"
    SyncReportFailureCategory.RECOVERY_REQUIRED -> "تحتاج المزامنة إلى استعادة حالتها قبل المتابعة"
    SyncReportFailureCategory.LOCAL_STORAGE -> "تعذّر الوصول إلى بيانات الجهاز أثناء المزامنة"
    SyncReportFailureCategory.UNKNOWN -> "تعذّرت بعض عمليات المزامنة"
    null -> null
}

fun formatDateTime(value: Long?): String {
    if (value == null || value <= 0L) return "—"
    return java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale("ar"))
        .format(java.util.Date(value))
}

private val DOMAIN_ORDER = listOf(
    "parties", "sales_finance", "inventory", "logistics", "organization", "optimal", "other"
)

sealed class SyncState {
    object Idle : SyncState()
    data class Loading(val msg: String) : SyncState()
    data class Success(val msg: String) : SyncState()
    data class Error(val msg: String) : SyncState()
}
