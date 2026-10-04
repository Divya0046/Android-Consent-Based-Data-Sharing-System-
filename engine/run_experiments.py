"""
Runs every experiment called for in the research plan (RQ1-RQ4, sections
10-15) against the ported PolicyEngine, and against two baseline consent
models for comparison. Produces CSVs, a JSON summary and charts under
./results, ready to paste into the paper.

Usage: python3 run_experiments.py
"""

import copy
import csv
import json
import os
import random
import statistics
import sys
import time

from policy_engine import (
    DataBridge,
    DataSafetyRepository,
    DataShareRequest,
    Policy,
    PolicyEngine,
    DisclosureOnlyBaseline,
    AppLevelCoarseBaseline,
    CoarseDataBridge,
)

random.seed(42)

HERE = os.path.dirname(os.path.abspath(__file__))
POLICIES_JSON = os.path.join(HERE, "policies.json")
OUT = os.path.join(HERE, "results")
os.makedirs(OUT, exist_ok=True)


def load_repo():
    return DataSafetyRepository(POLICIES_JSON)


# ---------------------------------------------------------------------------
# RQ2 / Section 10 — Core functional test cases (the 6 toy cases from the
# research plan), run against the real ported engine.
# ---------------------------------------------------------------------------

def toy_test_cases(repo):
    bridge = DataBridge()
    engine = PolicyEngine(repo, bridge)

    results = []

    def run_case(name, consent, request, expected):
        bridge.clear_policies()
        if consent is not None:
            bridge.save_policy(consent)
        r = engine.validate_request(request)
        actual = "ALLOW" if r.allowed else "DENY"
        passed = actual == expected
        results.append(
            {
                "test": name,
                "expected": expected,
                "actual": actual,
                "pass": passed,
                "reason": r.reason,
                "latency_ns": r.latency_ns,
            }
        )

    # Need an app with sharedData for these synthetic cases; wrap the real
    # DSS lookup by monkey-patching a tiny fixture app into the repo isn't
    # necessary -- we pick a real app from policies.json that has a
    # (Name/Personal info/Analytics)-like record; if not present we inject
    # a controlled fixture app directly (still uses the identical engine).
    fixture_app_id = "test.fixture.app"
    repo._by_app[fixture_app_id.lower()] = {
        "appId": fixture_app_id,
        "appInfo": {
            "sharedData": [
                {"data": "Name", "optional": False, "purpose": "Analytics", "type": "Personal info"},
                {"data": "Location", "optional": False, "purpose": "Analytics", "type": "Personal info"},
                {"data": "Name", "optional": False, "purpose": "Advertising or marketing", "type": "Personal info"},
            ]
        },
    }

    base = dict(appId=fixture_app_id, thirdParty="CompanyA", data="Name", category="Personal info", purpose="Analytics")

    # Test 1: Valid consent -> ALLOW
    consent1 = Policy(optional=False, allowed=True, **base)
    run_case("T1_valid_consent", consent1, DataShareRequest(**base), "ALLOW")

    # Test 2: Wrong third party -> DENY
    consent2 = Policy(optional=False, allowed=True, **base)
    req2 = dict(base); req2["thirdParty"] = "CompanyB"
    run_case("T2_wrong_third_party", consent2, DataShareRequest(**req2), "DENY")

    # Test 3: Wrong data -> DENY
    consent3 = Policy(optional=False, allowed=True, **base)
    req3 = dict(base); req3["data"] = "Location"
    run_case("T3_wrong_data", consent3, DataShareRequest(**req3), "DENY")

    # Test 4: No consent -> DENY
    run_case("T4_no_consent", None, DataShareRequest(**base), "DENY")

    # Test 5: Wrong purpose -> DENY
    consent5 = Policy(optional=False, allowed=True, **base)
    req5 = dict(base); req5["purpose"] = "Advertising or marketing"
    run_case("T5_wrong_purpose", consent5, DataShareRequest(**req5), "DENY")

    # Test 6: No declared third-party sharing (empty sharedData) -> DENY
    empty_app_id = "test.fixture.empty"
    repo._by_app[empty_app_id.lower()] = {"appId": empty_app_id, "appInfo": {"sharedData": []}}
    consent6 = Policy(appId=empty_app_id, thirdParty="CompanyA", data="Name", category="Personal info", purpose="Analytics", optional=False, allowed=True)
    req6 = DataShareRequest(appId=empty_app_id, thirdParty="CompanyA", data="Name", category="Personal info", purpose="Analytics")
    run_case("T6_no_declared_sharing", consent6, req6, "DENY")

    return results


# ---------------------------------------------------------------------------
# RQ1 / RQ2 at scale — build large-scale positive/negative test suites from
# the real 200-app / 100-with-sharedData dataset, so accuracy/false-allow/
# false-deny rates are computed over hundreds of real records rather than
# 6 toy cases. Also run the two comparison baselines on the same suite.
# ---------------------------------------------------------------------------

THIRD_PARTIES = ["AdNet", "AnalyticsCo", "CloudSync", "MarketingHub", "DataBroker"]


def build_large_scale_suite(repo, n_apps_cap=100):
    apps_with_data = repo.get_apps_with_shared_data()
    apps_with_data = apps_with_data[:n_apps_cap]

    consents = []          # Policy objects that WILL be stored (ground truth allowed)
    positive_requests = [] # requests that should be ALLOW
    negative_requests = [] # requests that should be DENY, tagged with violation type

    for app_id in apps_with_data:
        shared = repo.get_shared_data_by_package(app_id)
        if not shared:
            continue
        third_party = random.choice(THIRD_PARTIES)
        dss = random.choice(shared)

        consent = Policy(
            appId=app_id, thirdParty=third_party, data=dss.data,
            category=dss.category, purpose=dss.purpose.split(",")[0].strip(),
            optional=dss.optional, allowed=True,
        )
        consents.append(consent)

        pos_req = DataShareRequest(
            appId=app_id, thirdParty=third_party, data=dss.data,
            category=dss.category, purpose=dss.purpose.split(",")[0].strip(),
        )
        positive_requests.append(pos_req)

        # Negative: wrong third party
        other_tp = random.choice([t for t in THIRD_PARTIES if t != third_party])
        negative_requests.append(("wrong_third_party", DataShareRequest(
            appId=app_id, thirdParty=other_tp, data=dss.data,
            category=dss.category, purpose=dss.purpose.split(",")[0].strip(),
        )))

        # Negative: wrong data (use a different sharedData entry's data if available, else fabricate)
        other_dss = next((d for d in shared if d.data != dss.data), None)
        wrong_data = other_dss.data if other_dss else dss.data + "_X"
        negative_requests.append(("wrong_data", DataShareRequest(
            appId=app_id, thirdParty=third_party, data=wrong_data,
            category=dss.category, purpose=dss.purpose.split(",")[0].strip(),
        )))

        # Negative: wrong purpose
        negative_requests.append(("wrong_purpose", DataShareRequest(
            appId=app_id, thirdParty=third_party, data=dss.data,
            category=dss.category, purpose="Fabricated Unrelated Purpose",
        )))

        # Negative: undeclared sharing (random data/category not in DSS at all)
        negative_requests.append(("undeclared_sharing", DataShareRequest(
            appId=app_id, thirdParty=third_party, data="Precise location",
            category="Location", purpose="Advertising or marketing",
        )))

    # Negative: no consent at all, for an app that DOES have sharedData but
    # for which we never generated/stored any policy.
    no_consent_apps = repo.get_apps_with_shared_data()[n_apps_cap:n_apps_cap + 20]
    for app_id in no_consent_apps:
        shared = repo.get_shared_data_by_package(app_id)
        if not shared:
            continue
        dss = shared[0]
        negative_requests.append(("no_consent", DataShareRequest(
            appId=app_id, thirdParty=random.choice(THIRD_PARTIES), data=dss.data,
            category=dss.category, purpose=dss.purpose.split(",")[0].strip(),
        )))

    return consents, positive_requests, negative_requests


def evaluate_suite(name, validate_fn, positive_requests, negative_requests):
    tp = fn = tn = fp = 0
    latencies = []

    for req in positive_requests:
        r = validate_fn(req)
        latencies.append(r.latency_ns)
        if r.allowed:
            tp += 1
        else:
            fn += 1

    per_violation = {}
    for vtype, req in negative_requests:
        r = validate_fn(req)
        latencies.append(r.latency_ns)
        per_violation.setdefault(vtype, {"tn": 0, "fp": 0})
        if not r.allowed:
            tn += 1
            per_violation[vtype]["tn"] += 1
        else:
            fp += 1
            per_violation[vtype]["fp"] += 1

    total = tp + fn + tn + fp
    accuracy = 100.0 * (tp + tn) / total if total else 0.0
    false_allow_rate = 100.0 * fp / (fp + tn) if (fp + tn) else 0.0
    false_deny_rate = 100.0 * fn / (fn + tp) if (fn + tp) else 0.0

    return {
        "model": name,
        "total_requests": total,
        "true_positive_allow": tp,
        "false_negative_deny": fn,
        "true_negative_deny": tn,
        "false_positive_allow": fp,
        "authorization_accuracy_pct": round(accuracy, 3),
        "false_allow_rate_pct": round(false_allow_rate, 3),
        "false_deny_rate_pct": round(false_deny_rate, 3),
        "mean_latency_us": round(statistics.mean(latencies) / 1000, 4) if latencies else 0,
        "per_violation_type_false_allow": {
            k: v["fp"] for k, v in per_violation.items()
        },
    }


def run_comparison_experiment(repo):
    consents, positive_requests, negative_requests = build_large_scale_suite(repo)

    # --- Proposed fine-grained engine ---
    bridge = DataBridge()
    bridge.save_policies(consents)
    engine = PolicyEngine(repo, bridge)
    fine_grained_result = evaluate_suite(
        "Proposed (fine-grained DSS-based)", engine.validate_request, positive_requests, negative_requests
    )

    # --- Baseline A: disclosure-only (no runtime enforcement) ---
    baseline_a = DisclosureOnlyBaseline(repo)
    baseline_a_result = evaluate_suite(
        "Baseline A: Disclosure-only (status quo)", baseline_a.validate_request, positive_requests, negative_requests
    )

    # --- Baseline B: app-level coarse consent ---
    coarse_bridge = CoarseDataBridge()
    for c in consents:
        coarse_bridge.save(c.appId, c.thirdParty)
    baseline_b = AppLevelCoarseBaseline(coarse_bridge)
    baseline_b_result = evaluate_suite(
        "Baseline B: App-level coarse consent", baseline_b.validate_request, positive_requests, negative_requests
    )

    return [fine_grained_result, baseline_a_result, baseline_b_result], len(consents), len(positive_requests), len(negative_requests)


# ---------------------------------------------------------------------------
# RQ3 — Validation latency (repeated trials on a realistic policy store).
# ---------------------------------------------------------------------------

def run_latency_experiment(repo, n_policies=500, n_trials=2000):
    consents, positive_requests, negative_requests = build_large_scale_suite(repo, n_apps_cap=min(100, n_policies))
    bridge = DataBridge()
    bridge.save_policies(consents)
    engine = PolicyEngine(repo, bridge)

    all_requests = positive_requests + [r for _, r in negative_requests]
    if not all_requests:
        return []

    latencies_ns = []
    for _ in range(n_trials):
        req = random.choice(all_requests)
        r = engine.validate_request(req)
        latencies_ns.append(r.latency_ns)

    latencies_us = [x / 1000 for x in latencies_ns]
    return latencies_us


# ---------------------------------------------------------------------------
# RQ4 — Scalability: validation latency as stored policy count grows.
# ---------------------------------------------------------------------------

def run_scalability_experiment(repo, sizes=(10, 50, 100, 500, 1000, 5000), trials_per_size=500):
    apps_with_data = repo.get_apps_with_shared_data()
    rows = []

    for size in sizes:
        bridge = DataBridge()
        engine = PolicyEngine(repo, bridge)

        synthetic_consents = []
        i = 0
        while len(synthetic_consents) < size:
            app_id = apps_with_data[i % len(apps_with_data)]
            shared = repo.get_shared_data_by_package(app_id)
            if shared:
                dss = shared[i % len(shared)]
                tp = THIRD_PARTIES[i % len(THIRD_PARTIES)]
                synthetic_consents.append(
                    Policy(
                        appId=app_id, thirdParty=f"{tp}_{i}", data=dss.data,
                        category=dss.category, purpose=dss.purpose.split(",")[0].strip(),
                        optional=dss.optional, allowed=True,
                    )
                )
            i += 1
        bridge.save_policies(synthetic_consents)

        # Sample matched positions uniformly across the WHOLE store (not
        # just the first `trials_per_size` entries), so latency reflects the
        # true average/worst-case linear-scan cost at this store size
        # rather than being capped by trial count.
        latencies_ns = []
        for _ in range(trials_per_size):
            idx = random.randrange(len(synthetic_consents))
            c = synthetic_consents[idx]
            req = DataShareRequest(appId=c.appId, thirdParty=c.thirdParty, data=c.data, category=c.category, purpose=c.purpose)
            r = engine.validate_request(req)
            latencies_ns.append(r.latency_ns)

        latencies_us = [x / 1000 for x in latencies_ns]
        rows.append({
            "policy_count": size,
            "mean_latency_us": round(statistics.mean(latencies_us), 4),
            "median_latency_us": round(statistics.median(latencies_us), 4),
            "min_latency_us": round(min(latencies_us), 4),
            "max_latency_us": round(max(latencies_us), 4),
            "stdev_latency_us": round(statistics.stdev(latencies_us), 4) if len(latencies_us) > 1 else 0.0,
        })

    return rows


# ---------------------------------------------------------------------------
# Section 14 — Policy generation overhead vs number of sharedData entries.
# ---------------------------------------------------------------------------

def run_policy_generation_experiment(repo, trials=200):
    apps_with_data = repo.get_apps_with_shared_data()
    rows = []
    for app_id in apps_with_data:
        shared = repo.get_shared_data_by_package(app_id)
        n = len(shared)
        if n == 0:
            continue

        durations_ns = []
        for _ in range(trials):
            start = time.perf_counter_ns()
            # Mirrors MainActivity.generateConsent(): DSS read + combine with
            # a user-selected recipient -> list[Policy]
            shared_local = repo.get_shared_data_by_package(app_id)
            third_party = "CompanyX"
            policies = [
                Policy(appId=app_id, thirdParty=third_party, data=d.data,
                       category=d.category, purpose=d.purpose, optional=d.optional, allowed=True)
                for d in shared_local
            ]
            durations_ns.append(time.perf_counter_ns() - start)

        rows.append({
            "appId": app_id,
            "shared_data_entries": n,
            "mean_generation_latency_us": round(statistics.mean(durations_ns) / 1000, 4),
        })
    return rows


# ---------------------------------------------------------------------------
# Section 15 — Storage overhead as number of stored policies grows.
# ---------------------------------------------------------------------------

def run_storage_experiment(repo, sizes=(10, 50, 100, 500, 1000, 5000)):
    apps_with_data = repo.get_apps_with_shared_data()
    rows = []
    for size in sizes:
        bridge = DataBridge()
        i = 0
        synthetic = []
        while len(synthetic) < size:
            app_id = apps_with_data[i % len(apps_with_data)]
            shared = repo.get_shared_data_by_package(app_id)
            if shared:
                dss = shared[i % len(shared)]
                synthetic.append(Policy(
                    appId=app_id, thirdParty=f"Company_{i}", data=dss.data,
                    category=dss.category, purpose=dss.purpose.split(",")[0].strip(),
                    optional=dss.optional, allowed=True, createdAt=1700000000000 + i,
                ))
            i += 1
        bridge.save_policies(synthetic)
        size_bytes = bridge.storage_bytes()
        rows.append({
            "policy_count": size,
            "storage_bytes": size_bytes,
            "storage_kb": round(size_bytes / 1024, 3),
            "bytes_per_policy": round(size_bytes / size, 2),
        })
    return rows


# ---------------------------------------------------------------------------
# Output helpers
# ---------------------------------------------------------------------------

def write_csv(path, rows, fieldnames=None):
    if not rows:
        return
    fieldnames = fieldnames or list(rows[0].keys())
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames)
        w.writeheader()
        for r in rows:
            w.writerow(r)


def main():
    print("Loading policies.json ...")
    repo = load_repo()
    print(f"  {len(repo._apps)} apps loaded, {len(repo.get_apps_with_shared_data())} with sharedData")

    summary = {}

    print("\n[1/6] Toy functional test cases (Section 10) ...")
    toy_results = toy_test_cases(load_repo())
    write_csv(os.path.join(OUT, "functional_test_cases.csv"), toy_results)
    n_pass = sum(1 for r in toy_results if r["pass"])
    print(f"  {n_pass}/{len(toy_results)} passed")
    summary["functional_tests"] = {"passed": n_pass, "total": len(toy_results)}

    print("\n[2/6] Large-scale accuracy / false-allow / false-deny comparison (RQ1, RQ2) ...")
    comparison_rows, n_consents, n_pos, n_neg = run_comparison_experiment(load_repo())
    write_csv(os.path.join(OUT, "model_comparison.csv"), [
        {k: v for k, v in row.items() if k != "per_violation_type_false_allow"} for row in comparison_rows
    ])
    with open(os.path.join(OUT, "model_comparison_detail.json"), "w") as f:
        json.dump(comparison_rows, f, indent=2)
    print(f"  consents={n_consents} positive_requests={n_pos} negative_requests={n_neg}")
    for row in comparison_rows:
        print(f"   - {row['model']}: acc={row['authorization_accuracy_pct']}% "
              f"falseAllow={row['false_allow_rate_pct']}% falseDeny={row['false_deny_rate_pct']}%")
    summary["model_comparison"] = comparison_rows

    print("\n[3/6] Validation latency experiment (RQ3) ...")
    latencies_us = run_latency_experiment(load_repo(), n_policies=500, n_trials=3000)
    write_csv(os.path.join(OUT, "latency_raw_us.csv"), [{"latency_us": x} for x in latencies_us])
    lat_summary = {
        "n_trials": len(latencies_us),
        "mean_us": round(statistics.mean(latencies_us), 4),
        "median_us": round(statistics.median(latencies_us), 4),
        "min_us": round(min(latencies_us), 4),
        "max_us": round(max(latencies_us), 4),
        "stdev_us": round(statistics.stdev(latencies_us), 4),
        "p95_us": round(sorted(latencies_us)[int(0.95 * len(latencies_us)) - 1], 4),
        "p99_us": round(sorted(latencies_us)[int(0.99 * len(latencies_us)) - 1], 4),
    }
    print(f"  {lat_summary}")
    summary["latency"] = lat_summary

    print("\n[4/6] Scalability experiment (RQ4, Section 13) ...")
    scal_rows = run_scalability_experiment(load_repo())
    write_csv(os.path.join(OUT, "scalability.csv"), scal_rows)
    for r in scal_rows:
        print(f"  policies={r['policy_count']:>5}  mean={r['mean_latency_us']:>8} us  "
              f"median={r['median_latency_us']:>8} us  max={r['max_latency_us']:>8} us")
    summary["scalability"] = scal_rows

    print("\n[5/6] Policy generation overhead (Section 14) ...")
    gen_rows = run_policy_generation_experiment(load_repo())
    write_csv(os.path.join(OUT, "policy_generation.csv"), gen_rows)
    print(f"  measured over {len(gen_rows)} apps")
    summary["policy_generation_sample"] = gen_rows[:5]

    print("\n[6/6] Storage overhead (Section 15) ...")
    storage_rows = run_storage_experiment(load_repo())
    write_csv(os.path.join(OUT, "storage_overhead.csv"), storage_rows)
    for r in storage_rows:
        print(f"  policies={r['policy_count']:>5}  size={r['storage_kb']:>10} KB  "
              f"bytes/policy={r['bytes_per_policy']}")
    summary["storage_overhead"] = storage_rows

    with open(os.path.join(OUT, "summary.json"), "w") as f:
        json.dump(summary, f, indent=2)

    print(f"\nAll results written to {OUT}/")


if __name__ == "__main__":
    main()
