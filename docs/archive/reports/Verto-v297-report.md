# Verto v297 Execution Report

## Final verdict

**SESSION 297 = IMPLEMENTED_STATIC / BUILD_ENVIRONMENT_BLOCKED**

Static ownership migration is complete and all static gates pass. Full PASS is not claimed because Gradle could not bootstrap `gradle-8.9-bin.zip` in the offline execution environment (`UnknownHostException: services.gradle.org`). Under SESSION_297_FINAL §101 this output is **not** a final Source of Truth without a new explicit static-acceptance override.

## Input provenance

- Input: `Verto-v296-source-of-truth.zip`
- SHA-256: `a27632e255afaef7d70df94bb360ec1c2370b7e5e5fa275ed5b0c32e302b8544` (matched contract exactly)
- v296 status: `PASS_STATIC / SOURCE_OF_TRUTH_ACCEPTED_BY_EXPLICIT_USER_OVERRIDE`; Gradle/tests/compile were not claimed PASS in v296.
- Pre Migration Gate: `PASS`, snapshotId `79a6794fa3b540a15c80cc0f4f221afc569dbb8e3647cd6e81e97884aadd0e24`
- Pre Contract verifier: `DESIGN_SYSTEM_CONTRACT PASS 0`

## Pre/post scanner metrics

| Metric | Pre | Post |
|---|---:|---:|
| `legacyFocusedHardcodedCount` | 74 | 74 |
| `hardcodedUserFacingStringsFull` | 375 | 375 |
| `coreVisibleLiteralDebt` | 0 | 0 |
| `rawMaterialMustWrap` | 214 | 214 |
| `forbiddenMaterial` | 0 | 0 |
| `rawDpOutsideApprovedTokenFiles` | 22 | 22 |
| `rawSpOutsideApprovedTokenFiles` | 0 | 0 |
| `rawColorOutsideApprovedTokenFiles` | 0 | 0 |
| `rawMotionDurations` | 5 | 5 |
| `coreBoundaryViolations` | 0 | 0 |
| `designSystemDomainStringCount` | 820 | 0 |
| `externalDesignSystemDomainStringReferences` | 1214 | 0 |
| `expiredExceptions` | 0 | 0 |
| `permanentExceptions` | 0 | 0 |
| `duplicateGuardedPrimitives` | 0 | 0 |
| `activeExceptionEntryCount` | 29 | 29 |

Core strings pre-state: **836 = 16 canonical `verto_*` + 820 `ds_*`**. Core strings post-state: **16 canonical only; `ds_*` = 0**.

## Pre external reference inventory

| Module | FQ refs |
|---|---:|
| `feature/commission` | 99 |
| `feature/expenses` | 29 |
| `feature/integration/optimal` | 103 |
| `feature/inventory` | 165 |
| `feature/invoice` | 69 |
| `feature/messages` | 22 |
| `feature/party` | 211 |
| `feature/payment` | 107 |
| `feature/reports` | 144 |
| `feature/shipment` | 265 |
| **Total** | **1214** |

## Ownership classification

- `DESIGN_SYSTEM_GENERIC`: **5**
- `COMMON_APP_TEXT`: **31**
- `FEATURE_DOMAIN_TEXT`: **784**
- Unclassified: **0**
- Ownership manifest: `docs/design-system/STRING-OWNERSHIP-v297.json` — SHA-256 `f93fdf63a0efcdb3a0800f226224a073e5af45bd2cf8605ef8151739e163b42f` — 820 records.

### Canonical Design System mapping

| Legacy | Exact value | Target |
|---|---|---|
| `ds_2fa619787bcb` | التالي | `com.verto.core.designsystem.R.string.verto_action_next` |
| `ds_328ddce5bbca` | رجوع | `com.verto.core.designsystem.R.string.verto_navigate_back` |
| `ds_911aafd4ce2b` | تأكيد | `com.verto.core.designsystem.R.string.verto_action_confirm` |
| `ds_c10b04f72ce2` | اختياري | `com.verto.core.designsystem.R.string.verto_field_optional` |
| `ds_e776b0209b50` | إلغاء | `com.verto.core.designsystem.R.string.verto_action_cancel` |

### Common app mapping

| Legacy | Exact value | Target |
|---|---|---|
| `ds_070521f0f935` | المبلغ | `com.verto.core.common.R.string.common_amount` |
| `ds_0a92494ea1eb` | الاسم | `com.verto.core.common.R.string.common_name` |
| `ds_0d97fd6e069e` | مسح | `com.verto.core.common.R.string.common_action_clear` |
| `ds_11fdef2dc5f8` | الكل | `com.verto.core.common.R.string.common_all` |
| `ds_13ede87fcbfb` | حالة المزامنة | `com.verto.core.common.R.string.common_sync_status` |
| `ds_1e0455cba6d0` | المجموع | `com.verto.core.common.R.string.common_sum` |
| `ds_211cce4ca4ef` | رقم الهاتف | `com.verto.core.common.R.string.common_phone_number` |
| `ds_2d2bbdc2d694` | حذف | `com.verto.core.common.R.string.common_action_delete` |
| `ds_2e47b5f289f1` | إعادة المحاولة | `com.verto.core.common.R.string.common_action_retry` |
| `ds_413c51af19b5` | الإجمالي | `com.verto.core.common.R.string.common_total` |
| `ds_4309a75e6882` | تحديث | `com.verto.core.common.R.string.common_action_refresh` |
| `ds_4741fe022735` | لا توجد بيانات | `com.verto.core.common.R.string.common_no_data` |
| `ds_56ee6e0d206b` | حفظ | `com.verto.core.common.R.string.common_action_save` |
| `ds_5bf826c5e57c` | إغلاق | `com.verto.core.common.R.string.common_action_close` |
| `ds_5e3a3fdfce20` | إضافة | `com.verto.core.common.R.string.common_action_add` |
| `ds_7b5629bcb45d` | واتساب | `com.verto.core.common.R.string.common_whatsapp` |
| `ds_7c75fec5c0f8` | التصنيف | `com.verto.core.common.R.string.common_category` |
| `ds_8fca520bb27c` | ملاحظة | `com.verto.core.common.R.string.common_note` |
| `ds_90cf87a4177f` | إرسال | `com.verto.core.common.R.string.common_action_send` |
| `ds_b177f0b389bb` | تطبيق | `com.verto.core.common.R.string.common_action_apply` |
| `ds_b1f6bc266efe` | متابعة | `com.verto.core.common.R.string.common_follow_up` |
| `ds_b257b4e1692f` | إزالة | `com.verto.core.common.R.string.common_action_remove` |
| `ds_b4f76c3aa21e` | تعديل | `com.verto.core.common.R.string.common_action_edit` |
| `ds_b9357d9161f2` | العملة | `com.verto.core.common.R.string.common_currency` |
| `ds_ba3add142098` | بحث | `com.verto.core.common.R.string.common_search` |
| `ds_d4c6598d6ffa` | حسناً | `com.verto.core.common.R.string.common_ok` |
| `ds_dcc266785125` | معاينة | `com.verto.core.common.R.string.common_preview` |
| `ds_e85b74a3d0e3` | ملاحظة (اختياري) | `com.verto.core.common.R.string.common_note_optional` |
| `ds_f8a9e7d05e78` | فتح | `com.verto.core.common.R.string.common_action_open` |
| `ds_f96cfbf1d76a` | اتصال | `com.verto.core.common.R.string.common_action_call` |
| `ds_fb92e7003c18` | الدولة | `com.verto.core.common.R.string.common_country` |

## Feature-domain targets

| Module | New local definitions |
|---|---:|
| `feature/commission` | 74 |
| `feature/expenses` | 20 |
| `feature/integration/optimal` | 69 |
| `feature/inventory` | 111 |
| `feature/invoice` | 50 |
| `feature/messages` | 17 |
| `feature/party` | 98 |
| `feature/payment` | 80 |
| `feature/reports` | 128 |
| `feature/shipment` | 185 |
| **Total** | **832** |

## Reference replacement proof

- FQ FEATURE_DOMAIN_TEXT: **981**
- FQ COMMON_APP_TEXT: **126**
- FQ DESIGN_SYSTEM_GENERIC: **107**
- Already-local COMMON: **41**
- Already-local DS_GENERIC: **23**
- Total Kotlin replacements: **1278**
- Post `com.verto.core.designsystem.R.string.ds_*`: **0**
- Post core `name="ds_*"`: **0**
- All 38 local shadow definitions for the 36 special IDs were removed.

## Runtime value and placeholder preservation

- All **832** feature-domain targets were copied from the original core XML value and verified normalized-value identical.
- All **31** common targets were verified normalized-value identical.
- All **5** canonical mappings were verified value-equivalent to the pre-existing `verto_*` targets.
- Placeholder signatures were compared pre/post for all newly created targets; mismatches: **0**.
- No copy rewrite, translation rewrite, or hardcoded fallback was introduced.

## Duplicate-resource analysis

- Pre duplicate resource names overall: **48**; post: **12**.
- Pre duplicate `ds_*` names outside `core/designsystem`: **23**; post: **12**.
- The **11** v297-overlap duplicates disappeared.
- The **12** pre-existing unrelated local duplicates remain unchanged/out of scope.
- New duplicate target names created by v297: **0**.

## v291 ledger coupling

- Pre ledger SHA-256: `0af40bf47522157e1eabb9a704dfdf327dd7c6421da5da0f773597c7496e5bc8`
- Post ledger SHA-256: `586f892a040aaf83d983c5658b25aa39004b72e46dc9da89c80c78d01d8c8879`
- Parsed semantic diff: **9 changes**, all and only `sourceSha256`; every other field unchanged.
- Hardcoded debt counts for affected files remain unchanged; post scanner keeps `legacyFocusedHardcodedCount=74`, `hardcodedUserFacingStringsFull=375`, and active entries **29**.
| File | Rule | Pre sourceSha256 | Post sourceSha256 |
|---|---|---|---|
| `feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/inventory/InventoryDialogComponents.kt` | `HARDCODED_TEXT_LITERAL` | `1a55f456566039b233144e89f02333ca22920d02df1b6a6fe821f1658fb6c0af` | `ce109e350cc37d6ebff55af9b7a21ffc090f55810fc83900e1ce62a7ffbd241f` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientPaymentComponents.kt` | `HARDCODED_TEXT_LITERAL` | `5c729f064464a18923ffcc0ef15aab221b6548fca0ce919ee48c43d7ebe18a3e` | `569749809bc6c08628b8c595d9d78016ba02c86c1aedf935f95d91ece06e8241` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientPaymentScreen.kt` | `HARDCODED_SEMANTIC_LABEL` | `55b67e4f6b8226c3436b3bd3b7e917df33a04d8916e69c04f40e20592a6e8aab` | `b5361507c941304246fc461e1a07f1a1ee038422065e2588fc980058ba1a26ae` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientPaymentScreen.kt` | `HARDCODED_TEXT_LITERAL` | `55b67e4f6b8226c3436b3bd3b7e917df33a04d8916e69c04f40e20592a6e8aab` | `b5361507c941304246fc461e1a07f1a1ee038422065e2588fc980058ba1a26ae` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientsListScreen.kt` | `HARDCODED_TEXT_LITERAL` | `f3b944194d4dfbef7673f1b4678bc98a5481cfaa6c9c3e195655e59abd620117` | `7c00711e303a7e7e34bbf1d8e6116cb595b9f22e748229b9f40bff5ec91426c2` |
| `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierPaymentScreen.kt` | `HARDCODED_SEMANTIC_LABEL` | `172bdc310c3ac736a0af7cf3b0ac2437a5463dfb5f80f86cfc79691827183b9a` | `ad019841a4db5c72b9b7f8a145913b0a8579478c4e60a9835eb769880ed7c371` |
| `feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/adddebt/AddDebtFormComponents.kt` | `HARDCODED_TEXT_LITERAL` | `0b41099091c999070f89c357ee553fa079a90e0373864fcc8d3feee38da3123e` | `079d4cf1e64d35f66206826b4dc878dc3bf324eda329d8dd1727c0832973a2bb` |
| `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsShipmentDetailDialogs.kt` | `HARDCODED_TEXT_LITERAL` | `03a20e38e253666f838c05c30df022750897f7b469966d8dbc783767d26211c3` | `1983df1a470cf76777148aef051ff1435a58df70adf8c7f34017eec2639c1ea6` |
| `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsShipmentDetailSections.kt` | `HARDCODED_TEXT_LITERAL` | `7e19bf27217ddacea86c27161d40d5d18f8a6b41619226dce139a13744232e28` | `151ed5077d658aa1f33000b08e63e7aab02a985875d7c3e6b629dafab7132371` |

## Protected governance hashes

All protected files remained byte-identical.

| File | Pre/Post SHA-256 |
|---|---|
| `docs/design-system/BASELINE.json` | `af64cc9250e9baa8e45bcfd58b434d9897df210bae774d6bf1532d9b2b5e659d` |
| `scripts/design-system-scan.py` | `12110b782b8e395becdd6b149262a20f2f75c43c2649c13e09b2903dd646e72c` |
| `config/design-system/v226-enforcement-exceptions.json` | `45f316b2728a7e52972e47532d590aae0b526c31b2db72fec8e31de795f46413` |
| `config/design-system/final-zero-targets.json` | `1a1c853f0ae3380b3c2073b97cbaf1c7ff00fbf2dd51d16249e67214b2991175` |
| `config/design-system/material-usage-policy.json` | `fb73079486af45d54fc74705f1e33ca7d40f573c3b3f3d1c1c764ba9da02f989` |
| `tools/design_system_diff_gate.py` | `52c19df6b5ff6210b6551b90bc14805037fc1aa0a9b0e0071c822488dacee53a` |
| `scripts/ci/run-quality-gate.sh` | `d3a00384e25a73aa8f99ab518809b51ef3ab9e185288e15e8d38a37dc75301bf` |

## Static gates

- Migration Gate: **PASS**, snapshotId `ead112b3049b3150b9af7915ba149e5f85a88c7f76d031760a2a5c20556a8938`
- Diff Gate: **PASS**
- Contract verifier: **DESIGN_SYSTEM_CONTRACT PASS 0**
- CURRENT-DESIGN-STATE and CURRENT-HARDCODED-MANIFEST were regenerated from the same post snapshot.
- Baseline/scanner/v226/final-zero targets/material policy/diff tool/quality gate: unchanged.

## Gradle/resource/compile/test gates

- `./gradlew --no-daemon :app:mergeDebugResources`: **BLOCKED** before Gradle execution; wrapper attempted `https://services.gradle.org/distributions/gradle-8.9-bin.zip` and failed with `java.net.UnknownHostException: services.gradle.org`.
- Required module/app compilation: **NOT RUN / BLOCKED BY SAME GRADLE BOOTSTRAP PREREQUISITE**. No compile PASS is claimed.
- `:core:designsystem:testDebugUnitTest`: **NOT RUN / BLOCKED BY SAME GRADLE BOOTSTRAP PREREQUISITE**. No unit-test PASS is claimed.
- No Gradle files, repositories, versions, or dependencies were modified.

## Files actually changed

Changed production/evidence files relative to v296 before this report: **151 changed + 7 added**. Kotlin changes = **133**, exactly the 133-file session allowlist.

### Added files
- `core/common/src/main/res/values/strings.xml`
- `docs/design-system/STRING-OWNERSHIP-v297.json`
- `feature/commission/src/main/res/values/strings.xml`
- `feature/expenses/src/main/res/values/strings.xml`
- `feature/integration/optimal/src/main/res/values/strings.xml`
- `feature/inventory/src/main/res/values/strings.xml`
- `feature/messages/src/main/res/values/strings.xml`

### Changed non-Kotlin files
- `app/src/main/res/values/strings.xml`
- `config/design-system/v291-exceptions.json`
- `core/designsystem/src/main/res/values/strings.xml`
- `docs/design-system/CURRENT-DESIGN-STATE.md`
- `docs/design-system/CURRENT-HARDCODED-MANIFEST.json`
- `docs/design-system/DESIGN_SYSTEM_CONTRACT.md`
- `docs/design-system/MIGRATION-LEDGER.md`
- `feature/auth/src/main/res/values/strings.xml`
- `feature/dashboard/src/main/res/values/strings.xml`
- `feature/invoice/src/main/res/values/strings.xml`
- `feature/management/src/main/res/values/strings.xml`
- `feature/organization/src/main/res/values/strings.xml`
- `feature/party/src/main/res/values/strings.xml`
- `feature/payment/src/main/res/values/strings.xml`
- `feature/profile/src/main/res/values/strings.xml`
- `feature/reports/src/main/res/values/strings.xml`
- `feature/settings/src/main/res/values/strings.xml`
- `feature/shipment/src/main/res/values/strings.xml`

### Changed Kotlin files (exact allowlist)
- `app/src/main/kotlin/com/verto/app/ui/components/NavigationDrawerContent.kt`
- `app/src/main/kotlin/com/verto/app/ui/components/ReportsFilterBar.kt`
- `app/src/main/kotlin/com/verto/app/ui/integration/optimal/OptimalCompanyInvoiceRouteScreen.kt`
- `app/src/main/kotlin/com/verto/app/ui/screens/auditlog/AuditLogScreen.kt`
- `app/src/main/kotlin/com/verto/app/ui/screens/onboarding/SetupNameScreen.kt`
- `app/src/main/kotlin/com/verto/app/ui/screens/settings/InvoicePrintTabContent.kt`
- `app/src/main/kotlin/com/verto/app/ui/screens/settings/OrganizationPrintSettingsDialog.kt`
- `app/src/main/kotlin/com/verto/app/ui/screens/settings/SettingsRouteScreen.kt`
- `app/src/main/kotlin/com/verto/app/ui/screens/usersdashboard/UserDashboardDetailComponents.kt`
- `app/src/main/kotlin/com/verto/app/ui/screens/usersdashboard/UserDashboardDetailScreen.kt`
- `feature/auth/src/main/kotlin/com/verto/app/feature/auth/presentation/JoinOrgSections.kt`
- `feature/commission/src/main/kotlin/com/verto/app/ui/screens/commission/CommissionDialogs.kt`
- `feature/commission/src/main/kotlin/com/verto/app/ui/screens/commission/CommissionManagementScreen.kt`
- `feature/commission/src/main/kotlin/com/verto/app/ui/screens/commission/CommissionOverviewSections.kt`
- `feature/commission/src/main/kotlin/com/verto/app/ui/screens/commission/CommissionSheets.kt`
- `feature/commission/src/main/kotlin/com/verto/app/ui/screens/commission/WithdrawFlowDialogs.kt`
- `feature/commission/src/main/kotlin/com/verto/app/ui/screens/commissionreport/MarketerCommissionReportScreen.kt`
- `feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/presentation/education/EducationalTopicsScreen.kt`
- `feature/dashboard/src/main/kotlin/com/verto/app/ui/screens/usersdashboard/UsersDashboardSections.kt`
- `feature/expenses/src/main/kotlin/com/verto/app/ui/screens/expenses/ExpensesDialogs.kt`
- `feature/expenses/src/main/kotlin/com/verto/app/ui/screens/expenses/ExpensesScreen.kt`
- `feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/presentation/CompanyInvoicesScreen.kt`
- `feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/presentation/MaintenanceDetailsScreen.kt`
- `feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/presentation/MaintenanceRecordsScreen.kt`
- `feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/presentation/OptimalChatComponents.kt`
- `feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/presentation/OptimalChatScreen.kt`
- `feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/presentation/OptimalCodesScreen.kt`
- `feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/presentation/OptimalCompaniesScreen.kt`
- `feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/presentation/OptimalCompanyDetailsScreen.kt`
- `feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/presentation/OptimalConversationsScreen.kt`
- `feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/presentation/OptimalHomeScreen.kt`
- `feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/presentation/SyncIssuesScreen.kt`
- `feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/inventory/AddEditItemComponents.kt`
- `feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/inventory/AddEditItemContent.kt`
- `feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/inventory/AddEditItemSections.kt`
- `feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/inventory/BulkPriceEditScreen.kt`
- `feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/inventory/CategoryManagementScreen.kt`
- `feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/inventory/InventoryDialogComponents.kt`
- `feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/inventory/InventoryFilterComponents.kt`
- `feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/inventory/InventoryListComponents.kt`
- `feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/inventory/InventoryScreen.kt`
- `feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/inventory/InventoryScreenComponents.kt`
- `feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/inventory/InventorySummaryComponents.kt`
- `feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/pricelist/PriceListComponents.kt`
- `feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/pricelist/PriceListScreen.kt`
- `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/activeinvoices/ActiveInvoicesScreen.kt`
- `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceScreen.kt`
- `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceScreenComponents.kt`
- `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceTimerBadge.kt`
- `feature/management/src/main/kotlin/com/verto/app/feature/management/presentation/BenzineManagementScreen.kt`
- `feature/management/src/main/kotlin/com/verto/app/feature/management/presentation/ManagementScreen.kt`
- `feature/messages/src/main/kotlin/com/verto/app/ui/screens/messages/ChatDetailScreen.kt`
- `feature/messages/src/main/kotlin/com/verto/app/ui/screens/messages/MessagesScreen.kt`
- `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/OrganizationSettingsScreen.kt`
- `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/CreateInviteScreen.kt`
- `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/EmployeeDetailComponents.kt`
- `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/EmployeePermissionsScreen.kt`
- `feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/EmployeesScreen.kt`
- `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/addclient/AddEditClientComponents.kt`
- `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/addclient/AddEditClientScreen.kt`
- `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientDashboardComponents.kt`
- `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientDashboardScreen.kt`
- `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientPaymentComponents.kt`
- `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientPaymentScreen.kt`
- `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientScreen.kt`
- `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientScreenComponents.kt`
- `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientsListScreen.kt`
- `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/competitor/CompetitorScreen.kt`
- `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/shared/PartyDashboardComponents.kt`
- `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierDashboardScreen.kt`
- `feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierPaymentScreen.kt`
- `feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/adddebt/AddDebtDialogs.kt`
- `feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/adddebt/AddDebtDraftUi.kt`
- `feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/adddebt/AddDebtFormComponents.kt`
- `feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/adddebt/AddDebtItemComponents.kt`
- `feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/adddebt/AddDebtItemComposer.kt`
- `feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/adddebt/AddDebtScreen.kt`
- `feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/adddebt/AddDebtScreenContent.kt`
- `feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/adddebt/AddDebtScreenEffects.kt`
- `feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/adddebt/AddDebtSections.kt`
- `feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/adddebt/InvoiceMaintenanceSection.kt`
- `feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/payment/AddPaymentScreen.kt`
- `feature/profile/src/main/kotlin/com/verto/app/feature/profile/presentation/ProfileEditDialog.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/ReportsScreen.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/bottomsheets/BudgetEditorSheet.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/bottomsheets/CashCountSheet.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/core/AdvancedFiltersSheet.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/core/CollapsibleSection.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/core/HeroNetProfitCard.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/core/SmartInsightCarousel.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/AgedReceivablesCard.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/BudgetVsActualCard.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/FinancialIntegrityCard.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/AuditLogTodayCard.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/CashReconciliationCard.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/EmployeePerformanceCard.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/InventoryHealthCard.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/ReturnsAnalysisCard.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/ShipmentsSummaryCard.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/ShrinkageReportCard.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/SuppliersAnalysisCard.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/operations/XReportSnapshot.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/sales/CategoryDonutChart.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/sales/ClientSegmentsCard.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/sales/RealMarginCard.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/sales/SalesForecastCard.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/sales/SalesHeatmap.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/sales/TopItemsCard.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/export/ReportsExportFab.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/export/UnifiedExportDialog.kt`
- `feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/tabs/FinancialTab.kt`
- `feature/settings/src/main/kotlin/com/verto/app/feature/settings/presentation/InvoicePreviewTemplates.kt`
- `feature/settings/src/main/kotlin/com/verto/app/feature/settings/presentation/PriceListPreviewTemplates.kt`
- `feature/settings/src/main/kotlin/com/verto/app/feature/settings/presentation/SettingsBackupSection.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/CreateLogisticsShipmentScreen.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsCenterScreen.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsFormControls.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsShipmentDetailContent.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsShipmentDetailDialogs.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsShipmentDetailScreen.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsShipmentDetailSections.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsV239MovementExecutionSections.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsV239MovementPreparation.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsV2Components.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentCostSettlementScreen.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseCard.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseSheet.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseStep.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningScreen.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV237StationStep.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV237Steps.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV238Steps.kt`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentReceivingScreen.kt`

## Resource files created

- `core/common/src/main/res/values/strings.xml`
- `feature/commission/src/main/res/values/strings.xml`
- `feature/expenses/src/main/res/values/strings.xml`
- `feature/integration/optimal/src/main/res/values/strings.xml`
- `feature/inventory/src/main/res/values/strings.xml`
- `feature/messages/src/main/res/values/strings.xml`

## Out-of-scope observations

- Existing hardcoded user-facing debt remains **375 / focused 74** for Session 298.
- Raw Material debt remains **214** for Session 299.
- Raw dp remains **22** for Session 300.
- Raw motion remains **5** for Session 301.
- No business/data/navigation behavior was intentionally changed.

## Output eligibility

- Full Source-of-Truth output: **NOT ELIGIBLE** under Session 297 §101 because resource merge/compile could not run.
- A non-SoT `Verto-v297-implemented-static.zip` may be delivered for inspection/build elsewhere; its SHA-256 is recorded in the sidecar after packaging.
