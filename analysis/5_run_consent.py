import sys; sys.path.insert(0, "../engine")
"""Feeds ground_truth.csv + DSS JSON through the actual PolicyEngine port.
For each app: user profile picks which discovered third parties are allowed (popup simulation);
policies (consents) are generated for allowed third parties x DSS shared records;
then test requests are validated. Outputs consent_results.csv, popup_input.json, summary.csv"""
import csv, json, random, collections
from policy_engine import (DataSafetyRepository, DataBridge, PolicyEngine,
                           Policy, DataShareRequest)

repo = DataSafetyRepository("dss.json")
gt = collections.defaultdict(list)
for r in csv.DictReader(open("apps.csv")): gt[r["package"]]  # keep apps with 0 detections
for r in csv.DictReader(open("ground_truth.csv")): gt[r["app"]].append(r["third_party_entity"])
PROFILES = {"allow_all": lambda tps, rnd: set(tps),
            "deny_all": lambda tps, rnd: set(),
            "mixed": lambda tps, rnd: {t for t in tps if rnd.random() < 0.5}}
out, summ, popup = [], [], {}
for app, tps in gt.items():
    dss = repo.get_shared_data_by_package(app)
    popup[app] = {"third_parties": tps,
                  "declared_shared": [{"data": d.data, "category": d.category, "purpose": d.purpose} for d in dss]}
    for prof, pick in PROFILES.items():
        rnd = random.Random(42); allowed = pick(tps, rnd)
        bridge = DataBridge(); eng = PolicyEngine(repo, bridge)
        n_pol = 0
        for tp in allowed:
            for d in dss:
                bridge.save_policy(Policy(app, tp, d.data, d.category, d.purpose, d.optional, True, 0)); n_pol += 1
        tp_ok = tp_bad = fp = fn = 0
        for tp in tps:
            for d in dss:
                p = d.purpose.split(",")[0].strip()
                res = eng.validate_request(DataShareRequest(app, tp, d.data, d.category, p))
                expected = tp in allowed
                out.append([app, prof, tp, d.data, p, "allow" if expected else "deny",
                            "allow" if res.allowed else "deny", res.reason])
                if res.allowed and not expected: fp += 1
                if not res.allowed and expected: fn += 1
                tp_ok += res.allowed == expected
            # over-reach probe: data type never declared in sharedData
            res = eng.validate_request(DataShareRequest(app, tp, "Precise location__probe", "Location", "Advertising or marketing"))
            out.append([app, prof, tp, "UNDECLARED probe", "-", "deny", "allow" if res.allowed else "deny", res.reason])
            fp += res.allowed
        summ.append([app, prof, len(tps), len(dss), len(allowed), n_pol, tp_ok, fp, fn,
                     "declares NO shared data but %d SDKs detected" % len(tps) if not dss else ""])
csv.writer(open("consent_results.csv", "w", newline="")).writerows(
    [["app", "profile", "third_party", "data", "purpose", "expected", "actual", "reason"]] + out)
csv.writer(open("summary.csv", "w", newline="")).writerows(
    [["app", "profile", "third_parties_detected", "dss_shared_records", "tp_allowed_by_user",
      "consents_generated", "correct_decisions", "false_allows", "false_denies", "gap_note"]] + summ)
json.dump(popup, open("popup_input.json", "w"), indent=1)
print("done:", len(out), "requests;", sum(r[7] for r in summ), "false allows;", sum(r[8] for r in summ), "false denies")
