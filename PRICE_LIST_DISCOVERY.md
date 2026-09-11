# Price-list source discovery

## Candidate files
./app/src/main/res/values/strings.xml
./app/src/main/kotlin/com/verto/app/ui/navigation/InventoryShipmentsNavGraph.kt
./app/src/main/kotlin/com/verto/app/ui/navigation/search/AppSearchCatalog.kt
./app/src/main/kotlin/com/verto/app/ui/navigation/search/HomeSearchDestinationResolver.kt
./app/src/main/kotlin/com/verto/app/ui/navigation/DrawerDestinationRegistry.kt
./app/src/main/kotlin/com/verto/app/ui/navigation/QuickActionDestinationResolver.kt
./app/src/main/kotlin/com/verto/app/ui/navigation/Screen.kt
./app/src/main/kotlin/com/verto/app/ui/screens/settings/InvoicePrintTabContent.kt
./app/src/main/kotlin/com/verto/app/ui/screens/settings/InvoicePrintRouteScreen.kt
./app/src/main/kotlin/com/verto/app/data/backup/BackupManager.kt
./app/src/main/kotlin/com/verto/app/feature/settings/bridge/PrintingSettingsGatewayAdapter.kt
./data/preferences/src/main/kotlin/com/verto/app/utils/PreferencesManager.kt
./data/preferences/src/main/kotlin/com/verto/app/utils/PreferenceKeys.kt
./data/operations/src/main/kotlin/com/verto/app/application/presentationboundary/PresentationBoundaryAccess.kt
./data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncChangeApplier.kt
./data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt
./data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations25To33.kt
./data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations01To13.kt
./data/database/src/main/kotlin/com/verto/app/data/local/entity/PriceListEntity.kt
./data/database/src/main/kotlin/com/verto/app/data/local/dao/PriceListDao.kt
./data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations13To20.kt
./data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations90To91.kt
./data/database/src/main/kotlin/com/verto/data/database/di/DatabaseModule.kt
./data/database/src/androidTest/kotlin/com/verto/app/data/local/PriceListTemplateMigrationV376Test.kt
./feature/inventory/src/main/res/values/strings.xml
./feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/application/quickaction/InventoryQuickActionProvider.kt
./feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/application/InventoryPresentationServices.kt
./feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/application/port/InventoryPresentationPorts.kt
./feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/application/model/InventoryPresentationModels.kt
./feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/InventoryPresentationAdapters.kt
./feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/di/InventoryModule.kt
./feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/inventory/InventoryScreen.kt
./feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/inventory/InventoryScreenComponents.kt
./feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/pricelist/PriceListViewModel.kt
./feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/pricelist/PriceListComponents.kt
./feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/pricelist/PriceListScreen.kt
./feature/inventory/src/main/kotlin/com/verto/app/pdf/PriceListPdf.kt
./feature/settings/src/main/kotlin/com/verto/app/feature/settings/domain/repository/PrintingSettingsGateway.kt
./feature/settings/src/main/kotlin/com/verto/app/feature/settings/presentation/PriceListPreviewTemplates.kt
./feature/settings/src/main/kotlin/com/verto/app/feature/settings/presentation/printing/PrintingSettingsViewModel.kt
./feature/settings/src/main/kotlin/com/verto/app/feature/settings/presentation/InvoicePrintPreview.kt
./feature/dashboard/api/src/main/kotlin/com/verto/feature/dashboard/api/BusinessHomeSearchContracts.kt

## includeDate occurrences

## Price-list PDF/generation occurrences
./app/src/main/kotlin/com/verto/app/ui/screens/settings/InvoicePrintRouteScreen.kt:51:private val PRINT_TABS = listOf("فواتير", "كشف أسعار", "كشف حساب", "المخزون", "التقارير")
./app/src/main/kotlin/com/verto/app/data/backup/BackupManager.kt:267:        "قالب كشف أسعار يشير إلى قالب أو صنف غير موجود."
./data/preferences/src/main/kotlin/com/verto/app/utils/PreferencesManager.kt:73:    // ── Flows: كشف أسعار ──────────────────────────────────────────────────────────
./data/preferences/src/main/kotlin/com/verto/app/utils/PreferencesManager.kt:183:    // ── Setters: كشف أسعار ────────────────────────────────────────────────────────
./data/preferences/src/main/kotlin/com/verto/app/utils/PreferenceKeys.kt:40:// ── كشف أسعار — تفضيلات الطباعة ─────────────────────────────────────────
./feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/application/quickaction/InventoryQuickActionProvider.kt:35:                label = "كشف أسعار",
./feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/pricelist/PriceListScreen.kt:67:import com.verto.app.pdf.generatePriceListPdf
./feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/pricelist/PriceListScreen.kt:275:                                    generatePriceListPdf(
./feature/inventory/src/main/kotlin/com/verto/app/pdf/PriceListPdf.kt:42:fun generatePriceListPdf(
./feature/inventory/src/main/kotlin/com/verto/app/pdf/PriceListPdf.kt:77:        drawCenteredOld(canvas, "كشف أسعار", 58f, pricePaint)
./feature/settings/src/main/kotlin/com/verto/app/feature/settings/presentation/PriceListPreviewTemplates.kt:65:// ── Modern — كشف أسعار ─────────────────────────────────────────────────────────
./feature/settings/src/main/kotlin/com/verto/app/feature/settings/presentation/PriceListPreviewTemplates.kt:106:// ── Professional — كشف أسعار ───────────────────────────────────────────────────
./feature/settings/src/main/kotlin/com/verto/app/feature/settings/presentation/PriceListPreviewTemplates.kt:147:// ── Thermal/Luxury — كشف أسعار ─────────────────────────────────────────────────
./feature/settings/src/main/kotlin/com/verto/app/feature/settings/presentation/InvoicePrintPreview.kt:165:// ── Classic — كشف أسعار ────────────────────────────────────────────────────────
