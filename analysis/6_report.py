"""One-shot report for slides. Prints + saves report.csv and report.txt. Run after step 5."""
import csv, json, collections

apps = [r["package"] for r in csv.DictReader(open("apps.csv"))]
dss = {a["appId"]: a["appInfo"] for a in json.load(open("dss.json"))}
gt = collections.defaultdict(list)
for r in csv.DictReader(open("ground_truth.csv")): gt[r["app"]].append(r)
un = collections.defaultdict(list)
for r in csv.DictReader(open("unmapped_domains.csv")): un[r["app"]].append(r["domain"])
raw_static = collections.Counter(r["app"] for r in csv.DictReader(open("static_detected.csv")))

lines, rows = [], [["app", "dss_shared_records", "dss_collected", "entities_total", "static_only", "dynamic_only", "both", "unmapped_domains", "gap"]]
lines.append("%-36s %5s %5s | %5s %4s %4s %4s | %6s | gap" % ("app", "DSSsh", "DSScl", "total", "stat", "dyn", "both", "unmap"))
tot = collections.Counter()
for a in apps:
    g = gt.get(a, []); c = collections.Counter(r["detected_by"] for r in g)
    sh = len(dss.get(a, {}).get("sharedData", [])); cl = len(dss.get(a, {}).get("collectedData", []))
    gap = "YES: DSS declares no sharing, %d third parties found" % len(g) if sh == 0 and g else ("no third party found" if not g else "")
    lines.append("%-36s %5d %5d | %5d %4d %4d %4d | %6d | %s" % (a, sh, cl, len(g), c["static"], c["dynamic"], c["both"], len(un.get(a, [])), gap))
    rows.append([a, sh, cl, len(g), c["static"], c["dynamic"], c["both"], len(un.get(a, [])), gap])
    tot.update(c); tot["total"] += len(g)
lines.append("\nTOTAL entity pairs: %d  (static-only %d, dynamic-only %d, both %d)" % (tot["total"], tot["static"], tot["dynamic"], tot["both"]))
lines.append("Raw static SDK signatures matched: %d -> merged to %d entity pairs" % (sum(raw_static.values()), tot["total"]))
lines.append("\n--- third parties per app ---")
for a in apps:
    lines.append("%s: %s" % (a, "; ".join("%s[%s]" % (r["third_party_entity"], r["detected_by"]) for r in gt.get(a, [])) or "-"))
lines.append("\n--- dynamic domains NOT matched to a known tracker (first-party or unknown) ---")
for a in apps:
    if un.get(a): lines.append("%s: %s" % (a, ", ".join(sorted(set(un[a])))))
txt = "\n".join(lines); print(txt)
open("report.txt", "w", encoding="utf-8").write(txt)
csv.writer(open("report.csv", "w", newline="")).writerows(rows)
