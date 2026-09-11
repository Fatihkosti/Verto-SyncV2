#!/usr/bin/env python3
"""Deterministic local-prefix-search benchmark for session v94."""
from __future__ import annotations

import json
import sqlite3
import statistics
import time
from pathlib import Path

ROWS_PER_DOMAIN = 50_000
RUNS = 120
P95_LIMIT_MS = 50.0


def timed_ms(conn: sqlite3.Connection, sql: str, params: tuple[str, ...]) -> list[float]:
    conn.execute(sql, params).fetchall()  # warmup
    samples: list[float] = []
    for _ in range(RUNS):
        start = time.perf_counter_ns()
        conn.execute(sql, params).fetchall()
        samples.append((time.perf_counter_ns() - start) / 1_000_000)
    return samples


def percentile95(values: list[float]) -> float:
    return statistics.quantiles(values, n=100, method="inclusive")[94]


def main() -> None:
    conn = sqlite3.connect(":memory:")
    conn.executescript(
        """
        PRAGMA journal_mode=OFF;
        PRAGMA synchronous=OFF;
        CREATE TABLE clients(id TEXT PRIMARY KEY, nameSearch TEXT NOT NULL, phoneSearch TEXT NOT NULL);
        CREATE INDEX index_clients_name_search ON clients(nameSearch);
        CREATE INDEX index_clients_phone_search ON clients(phoneSearch);
        CREATE TABLE inventory_items(
          id TEXT PRIMARY KEY, nameSearch TEXT NOT NULL,
          partNumberSearch TEXT NOT NULL, barcodeSearch TEXT NOT NULL
        );
        CREATE INDEX index_inventory_items_name_search ON inventory_items(nameSearch);
        CREATE INDEX index_inventory_items_part_number_search ON inventory_items(partNumberSearch);
        CREATE INDEX index_inventory_items_barcode_search ON inventory_items(barcodeSearch);
        CREATE TABLE invoices(id TEXT PRIMARY KEY, invoiceNumberSearch TEXT NOT NULL, createdAt INTEGER NOT NULL);
        CREATE INDEX index_invoices_number_search ON invoices(invoiceNumberSearch);
        """
    )
    conn.executemany(
        "INSERT INTO clients VALUES (?,?,?)",
        ((f"c{i}", f"احمد {i:05d}" if i % 100 == 0 else f"عميل {i:05d}", f"2499{i:08d}") for i in range(ROWS_PER_DOMAIN)),
    )
    conn.executemany(
        "INSERT INTO inventory_items VALUES (?,?,?,?)",
        ((f"i{i}", f"فلتر {i:05d}", f"ty{i:06d}", f"629{i:09d}") for i in range(ROWS_PER_DOMAIN)),
    )
    conn.executemany(
        "INSERT INTO invoices VALUES (?,?,?)",
        ((f"n{i}", str(100_000 + i), i) for i in range(ROWS_PER_DOMAIN)),
    )
    conn.commit()

    queries = {
        "clients_name": (
            "SELECT id FROM clients WHERE (length(?) >= 2 AND nameSearch >= ? AND nameSearch < (? || char(1114111))) "
            "OR (length(?) >= 2 AND phoneSearch >= ? AND phoneSearch < (? || char(1114111))) LIMIT 9",
            ("اح", "اح", "اح", "", "", ""),
        ),
        "inventory_barcode": (
            "SELECT id FROM inventory_items WHERE (length(?) >= 2 AND nameSearch >= ? AND nameSearch < (? || char(1114111))) "
            "OR (length(?) >= 2 AND partNumberSearch >= ? AND partNumberSearch < (? || char(1114111))) "
            "OR (length(?) >= 2 AND barcodeSearch >= ? AND barcodeSearch < (? || char(1114111))) LIMIT 9",
            ("", "", "", "62942", "62942", "62942", "62942", "62942", "62942"),
        ),
        "invoice_number": (
            "SELECT id FROM invoices WHERE length(?) >= 2 AND invoiceNumberSearch >= ? AND invoiceNumberSearch < (? || char(1114111)) "
            "ORDER BY createdAt DESC LIMIT 9",
            ("123", "123", "123"),
        ),
    }

    results: dict[str, object] = {
        "rows_per_domain": ROWS_PER_DOMAIN,
        "total_rows": ROWS_PER_DOMAIN * 3,
        "runs_per_query": RUNS,
        "p95_limit_ms": P95_LIMIT_MS,
        "queries": {},
    }
    for name, (sql, params) in queries.items():
        samples = timed_ms(conn, sql, params)
        plan = [row[3] for row in conn.execute("EXPLAIN QUERY PLAN " + sql, params)]
        p95 = percentile95(samples)
        results["queries"][name] = {
            "median_ms": round(statistics.median(samples), 4),
            "p95_ms": round(p95, 4),
            "max_ms": round(max(samples), 4),
            "query_plan": plan,
            "uses_search_index": any("INDEX" in step and "search" in step for step in plan),
            "passes": p95 <= P95_LIMIT_MS,
        }

    output = Path("docs/benchmarks/v94-search-results.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(results, ensure_ascii=False))

    assert all(item["uses_search_index"] for item in results["queries"].values()), results
    assert all(item["passes"] for item in results["queries"].values()), results
    print(f"V94_SEARCH_BENCHMARK_RESULT {len(results['queries'])}/{len(results['queries'])} passed; rows={results['total_rows']}")


if __name__ == "__main__":
    main()
