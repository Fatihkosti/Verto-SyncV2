#!/usr/bin/env bash
set -euo pipefail

fail() { echo "FAIL: $1" >&2; exit 1; }
pass() { echo "PASS: $1"; }

bash ./scripts/verify-party-v345.sh >/dev/null
pass "Session 345 customer decision engine retained"

catalog=data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt
migration=data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations83To84.kt
entity=data/database/src/main/kotlin/com/verto/app/data/local/entity/PurchaseCycleEntities.kt
dto=data/network/src/main/kotlin/com/verto/app/data/remote/dto/PurchaseCycleDtos.kt
coordinator=feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/PurchaseCycleCoordinator.kt
engine=feature/party/src/main/kotlin/com/verto/app/feature/party/application/intelligence/SupplierIntelligenceEngine.kt
evidence=feature/party/src/main/kotlin/com/verto/app/feature/party/application/port/SupplierIntelligenceEvidence.kt
source=feature/party/src/main/kotlin/com/verto/app/feature/party/application/port/SupplierIntelligenceSource.kt
adapter=app/src/main/kotlin/com/verto/app/feature/party/bridge/AppSupplierIntelligenceSource.kt
service=feature/party/src/main/kotlin/com/verto/app/feature/party/application/PartyApplicationService.kt
server=docs/sql/v346_supplier_intelligence.sql

grep -q 'ROOM_SCHEMA_VERSION: Int = 84' "$catalog" || fail "Room schema 84"
grep -q 'MIGRATION_83_84' "$catalog" || fail "83->84 migration catalog"
grep -q 'promised_delivery_at' "$migration" || fail "Room promised delivery migration"
grep -q 'promisedDeliveryAt' "$entity" || fail "PO promised delivery entity field"
grep -q 'promised_delivery_at' "$dto" || fail "PO promised delivery transport field"
grep -q '"promisedDeliveryAt" to order.promisedDeliveryAt' "$coordinator" || fail "unified PO payload promise"
grep -q 'promised_delivery_at' "$server" || fail "server migration promise"
pass "promised delivery is explicit across Room/domain/sync/server contract"

for marker in \
  'qualityRateBps' \
  'acceptedFillRateBps' \
  'purchaseReturnRateBps' \
  'priceVarianceBps' \
  'averageLeadTimeDays' \
  'leadTimeVariabilityDays' \
  'onTimeDeliveryRateBps' \
  'matchedInvoiceCostByCurrencyMinor' \
  'LANDED_COST_NOT_ATTRIBUTABLE_TO_SUPPLIER' \
  'MULTI_CURRENCY_PRICE_VARIANCE_UNAVAILABLE'; do
  grep -q "$marker" "$engine" || fail "supplier KPI marker $marker"
done
pass "supplier scorecard has quality/fill/returns/price/lead-time/cost safeguards"

grep -q 'recommendForItem' "$engine" || fail "item supplier recommendation engine"
grep -q 'minimumOrdersForComparison' "$engine" || fail "comparison evidence threshold"
grep -q 'bestSupplierId' "$engine" || fail "best supplier result"
pass "item-level supplier ranking exists"

grep -q 'interface SupplierIntelligenceSource' "$source" || fail "Party-owned source port"
grep -q 'class AppSupplierIntelligenceSource' "$adapter" || fail "app composition adapter"
grep -q 'observePurchaseReturnsForSupplierIntelligence' "$adapter" || fail "purchase return evidence wiring"
grep -q 'ObserveSupplierPerformanceUseCase' "$service" || fail "supplier performance use case integration"
grep -q 'ObserveBestSupplierForItemUseCase' "$service" || fail "best supplier use case integration"
pass "persistence is mapped through Party-owned read contract"

test=feature/party/src/test/kotlin/com/verto/app/feature/party/application/intelligence/SupplierIntelligenceEngine346Test.kt
[[ -f "$test" ]] || fail "Session 346 regression test missing"
for marker in \
  'supplier score uses quality fill price lead time and promised delivery' \
  'missing promised dates never invent on time delivery' \
  'multi currency history never mixes price variance amounts' \
  'purchase returns reduce effective quality and trigger review' \
  'best supplier for item requires comparable evidence and ranks by performance'; do
  grep -q "$marker" "$test" || fail "test marker: $marker"
done
pass "Session 346 regression scenarios present"

tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT
kotlinc "$evidence" "$engine" -d "$tmpdir/supplier346.jar"

cat >"$tmpdir/Harness.kt" <<'KOT'
package com.verto.app.feature.party.application.intelligence

import com.verto.app.feature.party.application.port.*

private const val D = 86_400_000L

fun main() {
    fun row(id: String, supplier: String, created: Long, promise: Long?, received: Long, accepted: Int, rejected: Int, po: Long, actual: Long) =
        SupplierOrderEvidence(
            orderId = id,
            supplierId = supplier,
            currencyCode = "SDG",
            status = "RECEIVED",
            createdAt = created,
            promisedDeliveryAt = promise,
            lines = listOf(SupplierOrderLineEvidence("l$id", "item", 10, po)),
            receipts = listOf(SupplierReceiptEvidence("r$id", "l$id", received, accepted + rejected, accepted, rejected, actual)),
            priceMatches = listOf(SupplierPriceEvidence("m$id", "l$id", 10, po, actual)),
        )

    val strong = listOf(
        row("1", "A", D, 6*D, 5*D, 10, 0, 100, 100),
        row("2", "A", 10*D, 15*D, 14*D, 10, 0, 100, 105),
        row("3", "A", 20*D, 25*D, 24*D, 10, 0, 100, 100),
    )
    val weak = listOf(
        row("4", "B", D, 6*D, 9*D, 8, 2, 100, 125),
        row("5", "B", 10*D, 15*D, 19*D, 8, 2, 100, 125),
    )

    val snapshot = SupplierIntelligenceEngine.evaluate(strong, 40*D)
    check(snapshot.onTimeDeliveryRateBps == 10_000)
    check(snapshot.priceVarianceBps == 166)
    check(SupplierIntelligenceEngine.evaluate(strong.map { it.copy(promisedDeliveryAt = null) }, 40*D).onTimeDeliveryRateBps == null)
    check(SupplierIntelligenceEngine.recommendForItem("item", strong + weak, 40*D).bestSupplierId == "A")
    println("V346_SUPPLIER_INTELLIGENCE_HARNESS=PASS")
}
KOT

kotlinc "$evidence" "$engine" "$tmpdir/Harness.kt" -include-runtime -d "$tmpdir/harness.jar"
java -jar "$tmpdir/harness.jar"

echo 'PARTY_346_STATIC_GATE=PASS'
