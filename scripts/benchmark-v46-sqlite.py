#!/usr/bin/env python3
"""بديل قابل للتكرار لـMacrobenchmark عند غياب Android runtime.

يقارن N+1 القديمة مع الاستعلام المجمع الحالي على قاعدة ممثلة كبيرة.
"""
from __future__ import annotations

import argparse
import json
import math
import sqlite3
import statistics
import time
from pathlib import Path

INVOICE_COUNT = 5_000
PAYMENTS_PER_INVOICE = 8
TARGET_INVOICES = 2_000
BATCH_SIZE = 500
ITERATIONS = 9


def percentile(values: list[float], percentile_value: float) -> float:
    ordered = sorted(values)
    index = min(len(ordered) - 1, math.ceil(percentile_value * len(ordered)) - 1)
    return ordered[index]


def legacy_n_plus_one(db: sqlite3.Connection, invoice_ids: list[str]) -> dict[str, float]:
    result: dict[str, float] = {}
    for invoice_id in invoice_ids:
        row = db.execute(
            "SELECT COALESCE(SUM(amount), 0) FROM payments WHERE invoiceId = ?",
            (invoice_id,),
        ).fetchone()
        result[invoice_id] = float(row[0])
    return result


def grouped_batches(db: sqlite3.Connection, invoice_ids: list[str]) -> dict[str, float]:
    result: dict[str, float] = {}
    for offset in range(0, len(invoice_ids), BATCH_SIZE):
        batch = invoice_ids[offset : offset + BATCH_SIZE]
        placeholders = ",".join("?" for _ in batch)
        rows = db.execute(
            f"""
            SELECT invoiceId, COALESCE(SUM(amount), 0) AS totalPaid
            FROM payments
            WHERE invoiceId IN ({placeholders})
            GROUP BY invoiceId
            """,
            batch,
        ).fetchall()
        result.update((str(invoice_id), float(total)) for invoice_id, total in rows)
    return result


def measure(callable_) -> tuple[list[float], dict[str, float]]:
    durations: list[float] = []
    result: dict[str, float] = {}
    for _ in range(ITERATIONS):
        start = time.perf_counter_ns()
        result = callable_()
        durations.append((time.perf_counter_ns() - start) / 1_000_000)
    return durations, result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    db = sqlite3.connect(":memory:")
    db.execute(
        "CREATE TABLE payments (id TEXT PRIMARY KEY NOT NULL, invoiceId TEXT NOT NULL, amount REAL NOT NULL, paidAt INTEGER NOT NULL)"
    )
    db.execute("CREATE INDEX index_payments_invoiceId ON payments(invoiceId)")
    rows = [
        (f"p-{invoice}-{payment}", f"inv-{invoice}", float(payment + 1), invoice * 10 + payment)
        for invoice in range(INVOICE_COUNT)
        for payment in range(PAYMENTS_PER_INVOICE)
    ]
    db.executemany("INSERT INTO payments VALUES (?, ?, ?, ?)", rows)
    db.commit()

    invoice_ids = [f"inv-{invoice}" for invoice in range(TARGET_INVOICES)]

    # Warm-up خارج القياس.
    legacy_n_plus_one(db, invoice_ids[:50])
    grouped_batches(db, invoice_ids[:50])

    legacy_times, legacy_result = measure(lambda: legacy_n_plus_one(db, invoice_ids))
    grouped_times, grouped_result = measure(lambda: grouped_batches(db, invoice_ids))

    if legacy_result != grouped_result:
        raise SystemExit("Grouped query result differs from N+1 result")

    plan = db.execute(
        "EXPLAIN QUERY PLAN SELECT invoiceId, SUM(amount) FROM payments WHERE invoiceId IN (?, ?) GROUP BY invoiceId",
        invoice_ids[:2],
    ).fetchall()
    plan_text = " | ".join(str(row) for row in plan)
    if "index_payments_invoiceId" not in plan_text:
        raise SystemExit(f"Expected payment index was not used: {plan_text}")

    legacy_median = statistics.median(legacy_times)
    grouped_median = statistics.median(grouped_times)
    speedup = legacy_median / grouped_median if grouped_median else float("inf")
    payload = {
        "dataset": {
            "invoices": INVOICE_COUNT,
            "payments": len(rows),
            "targetInvoices": TARGET_INVOICES,
            "batchSize": BATCH_SIZE,
            "iterations": ITERATIONS,
        },
        "legacyNPlusOne": {
            "queriesPerIteration": TARGET_INVOICES,
            "medianMs": round(legacy_median, 3),
            "p95Ms": round(percentile(legacy_times, 0.95), 3),
        },
        "groupedBatches": {
            "queriesPerIteration": math.ceil(TARGET_INVOICES / BATCH_SIZE),
            "medianMs": round(grouped_median, 3),
            "p95Ms": round(percentile(grouped_times, 0.95), 3),
        },
        "speedup": round(speedup, 2),
        "resultRows": len(grouped_result),
        "queryPlan": plan_text,
    }

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n")

    print(json.dumps(payload, ensure_ascii=False))
    print(
        f"V46_SQLITE_BENCHMARK_RESULT speedup={speedup:.2f}x "
        f"legacyMedianMs={legacy_median:.3f} groupedMedianMs={grouped_median:.3f} "
        f"queries={TARGET_INVOICES}->{math.ceil(TARGET_INVOICES / BATCH_SIZE)}"
    )


if __name__ == "__main__":
    main()
