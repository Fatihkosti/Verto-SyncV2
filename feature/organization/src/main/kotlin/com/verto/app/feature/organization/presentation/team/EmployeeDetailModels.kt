package com.verto.app.feature.organization.presentation.team

import com.verto.app.core.presentation.UiState
import com.verto.app.feature.organization.domain.model.EmployeePermissions
import com.verto.app.feature.organization.domain.model.OrganizationEmployee
import kotlin.math.abs

data class EmployeeDetailUiState(
    val profile: EmployeeDetailProfile = EmployeeDetailProfile(),
    val performance: SalesPerformance = SalesPerformance(),
    // حالة بطاقة الأداء الحقيقي (Employee Performance Binding)
    val performanceLoading: Boolean = false,
    val performanceError: String? = null,
    // الفترة الخام (millis) لتهيئة منتقي التاريخ في الواجهة
    val periodFrom: Long = 0L,
    val periodTo: Long = 0L,
    val attendance: AttendanceSummary = AttendanceSummary(),
    val permissionsSummary: PermissionsSummary = PermissionsSummary(),
    val communicationTemplates: List<NotificationTemplate> = emptyList(),
    val growth: GrowthAndTasks = GrowthAndTasks()
) : UiState

data class EmployeeDetailProfile(
    val name: String = "موظف",
    val jobTitle: String = "مبيعات",
    val joinedAt: String = "2026-05-01",
    val presence: String = "متصل الآن",
    val lastTransaction: String = "فاتورة بيع #1042 - اليوم 11:20 ص"
)

// أرقام الأداء الحقيقية — مُنسّقة بدون رمز عملة (قاعدة العمل: لا رموز عملة في الواجهة).
data class SalesPerformance(
    val fromDate: String = "",
    val toDate: String = "",
    val invoiceCount: Int = 0,
    val cashInvoices: Int = 0,
    val creditInvoices: Int = 0,
    val totalSales: String = "0",
    val totalProfit: String = "0",
    val debts: String = "0",
    val collections: String = "0",
    val addedClients: Int = 0
)

data class AttendanceSummary(
    val presentDays: Int = 24,
    val absentDays: Int = 2,
    val leaveDays: Int = 1,
    val commitmentRate: Int = 92
)

data class PermissionsSummary(
    val visibleItems: List<String> = emptyList(),
    val hiddenItems: List<String> = emptyList()
)

data class NotificationTemplate(
    val title: String,
    val body: String
)

data class GrowthAndTasks(
    val completedCourses: List<String> = emptyList(),
    val currentCourses: List<String> = emptyList(),
    val completedTasks: List<String> = emptyList(),
    val currentTasks: List<String> = emptyList()
)

fun buildFakeEmployeeDetail(
    userId: String,
    employee: OrganizationEmployee?,
    resolveString: (Int) -> String,
): EmployeeDetailUiState {
    val seed = abs(userId.hashCode())
    val roles = listOf("مبيعات", "مشتريات", "محاسب", "مخزن")
    val name = employee?.name?.takeIf { it.isNotBlank() } ?: "موظف Verto"
    val joinedAt = employee?.joinedAt?.substringBefore("T")?.takeIf { it.isNotBlank() } ?: "2026-05-01"
    val role = roles[seed % roles.size]

    return EmployeeDetailUiState(
        profile = EmployeeDetailProfile(
            name = name,
            jobTitle = role,
            joinedAt = joinedAt,
            presence = if (seed % 2 == 0) "متصل الآن" else "آخر ظهور: اليوم 9:45 ص",
            lastTransaction = if (role == "مبيعات") {
                "فاتورة بيع #${1040 + seed % 80} - اليوم 11:20 ص"
            } else {
                "تحديث مخزون #${220 + seed % 40} - أمس 4:10 م"
            }
        ),
        // الأداء يُملأ بأرقام حقيقية من Room في الـ ViewModel (لا بيانات وهمية هنا).
        performance = SalesPerformance(),
        attendance = AttendanceSummary(
            presentDays = 22 + seed % 4,
            absentDays = seed % 3,
            leaveDays = 1 + seed % 2,
            commitmentRate = 88 + seed % 10
        ),
        permissionsSummary = buildPermissionsSummary(employee?.permissions ?: EmployeePermissions.defaultEmployee(), resolveString),
        communicationTemplates = listOf(
            NotificationTemplate("تم صرف مرتب شهر مايو", "تم صرف مرتب شهر مايو، يمكنك مراجعة الحسابات عند الحاجة."),
            NotificationTemplate("تم صرف حافز العيد", "تم صرف حافز العيد تقديراً لمجهودك خلال الفترة الماضية."),
            NotificationTemplate("تم خصم غياب 3 أيام", "تم تسجيل خصم غياب 3 أيام حسب سجل الحضور للفترة الحالية."),
            NotificationTemplate("تكليف بمهمة", "تم تكليفك بمهمة جديدة، يرجى مراجعة المدير لمعرفة التفاصيل."),
            NotificationTemplate("رسالة مخصصة", "اكتب نص الرسالة المخصصة عند ربط نظام الإشعارات لاحقاً.")
        ),
        growth = GrowthAndTasks(
            completedCourses = listOf("أساسيات البيع", "إدارة العملاء"),
            currentCourses = listOf("تحصيل الديون باحتراف"),
            completedTasks = listOf("مراجعة عملاء الأسبوع", "تحديث بيانات 12 عميل"),
            currentTasks = listOf("متابعة فواتير الآجل", "إضافة 5 عملاء جدد")
        )
    )
}

private fun buildPermissionsSummary(permissions: EmployeePermissions, resolveString: (Int) -> String): PermissionsSummary {
    val allowed = mutableListOf<String>()
    val denied = mutableListOf<String>()

    buildPermissionSections(resolveString).forEach { section ->
        val enabledCount = section.subPermissions.count { it.getValue(permissions) }
        when {
            enabledCount == section.subPermissions.size -> allowed += section.label
            enabledCount == 0 -> denied += section.label
            else -> {
                allowed += "${section.label}: $enabledCount صلاحيات"
                denied += "${section.label}: ${section.subPermissions.size - enabledCount} غير متاحة"
            }
        }
    }

    return PermissionsSummary(
        visibleItems = allowed.ifEmpty { listOf("لا توجد صلاحيات مفعلة") },
        hiddenItems = denied.ifEmpty { listOf("لا توجد عناصر مخفية") }
    )
}
