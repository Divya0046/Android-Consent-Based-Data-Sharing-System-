# Experimental Results — Fine-Grained Consent Enforcement Framework

Generated from `run_experiments.py`, a faithful Python port of your Android
`PolicyEngine` / `DataBridge` / `DataSafetyRepository` (image-preview module),
run against your real 200-app `policies.json` dataset (100 apps have
non-empty `sharedData`). No emulator was needed — this reproduces the exact
matching algorithm your Kotlin code implements, decoupled from foreground-app
detection and Firebase per your own research-direction notes (sections 18, 22).

## RQ1 / RQ2 — Functional correctness (Section 10 toy cases)

**6 / 6 test cases pass** (T1 valid consent → ALLOW; T2 wrong third party,
T3 wrong data, T4 no consent, T5 wrong purpose, T6 no declared sharing → all
DENY). See `functional_test_cases.csv`.

## RQ1 / RQ2 at scale — Proposed framework vs. baseline consent models

Built from **100 real apps** with declared `sharedData`: 100 positive
(should-ALLOW) requests + 400 negative (should-DENY) requests covering wrong
third party, wrong data, wrong purpose, and undeclared-sharing violations.

| Model | Accuracy | False-Allow Rate | False-Deny Rate |
|---|---|---|---|
| **Proposed (fine-grained, DSS-based)** | **100.0%** | **0.0%** | 0.0% |
| Baseline A: Disclosure-only (today's Play Store status quo — no runtime gate) | 20.0% | 100.0% | 0.0% |
| Baseline B: App-level coarse consent (app+recipient only, ignores data/category/purpose) | 40.0% | 75.0% | 0.0% |

This is the headline evaluation table: it shows the cost, in concrete
false-allow terms, of *not* having recipient/data/category/purpose-level
enforcement. (Full breakdown by violation type in
`model_comparison_detail.json`.)

## RQ3 — Validation latency

3000 trials against a 500-policy store:

| Metric | Value |
|---|---|
| Mean | 162.3 µs |
| Median | 162.1 µs |
| p95 | 217.0 µs |
| p99 | 272.1 µs |
| Min / Max | 28.3 / 1592.3 µs |

See `chart_latency_distribution.png`, `latency_raw_us.csv`.

## RQ4 — Scalability

| Policies stored | Mean latency | Median | Max |
|---|---|---|---|
| 10 | 35.4 µs | 34.1 µs | 143.0 µs |
| 50 | 68.2 µs | 67.4 µs | 166.8 µs |
| 100 | 107.2 µs | 101.0 µs | 283.5 µs |
| 500 | 414.8 µs | 398.1 µs | 994.1 µs |
| 1000 | 875.8 µs | 873.2 µs | 2532.1 µs |
| 5000 | 4115.7 µs | 4122.1 µs | 13125.8 µs |

Latency grows **linearly** with stored policy count (≈0.83 µs/policy),
consistent with the linear-scan matching in `DataBridge.findMatchingPolicy` /
`PolicyEngine.validateRequest`. Worth naming explicitly as a limitation +
future-work item (e.g., indexing by `(appId, thirdParty)` to cut this to
near-constant time) — see `chart_scalability.png`.

## Section 14 — Policy generation overhead

Measured across all 100 apps with `sharedData` (1–12 entries each): generation
time scales with the number of declared `sharedData` records per app, on the
order of a few µs per entry. See `policy_generation.csv`,
`chart_policy_generation.png`.

## Section 15 — Storage overhead

| Policies stored | Size | Bytes/policy |
|---|---|---|
| 10 | 2.1 KB | 215.4 |
| 50 | 10.7 KB | 218.5 |
| 100 | 21.4 KB | 218.8 |
| 500 | 107.3 KB | 219.8 |
| 1000 | 214.9 KB | 220.1 |
| 5000 | 1079.0 KB (≈1.05 MB) | 221.0 |

Storage grows linearly at ~220 bytes/policy (JSON in `SharedPreferences`).
Even 5000 policies (an unrealistic upper bound for a single device) is
~1 MB — negligible on modern devices. See `chart_storage.png`.

## Files in this folder

- `policy_engine.py` — ported engine + two baseline models (source of truth for all numbers)
- `run_experiments.py` — runs everything, writes CSVs + `summary.json`
- `make_charts.py` — produces the 5 PNGs
- `results/` — all CSVs, JSON, and charts
