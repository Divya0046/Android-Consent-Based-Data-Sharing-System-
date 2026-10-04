"""Merge static + dynamic, resolve aliases to one entity, label detected_by.
Edit alias_map.json to merge names (regex -> entity). Output: ground_truth.csv, resolution_stats.json"""
import csv, json, re, collections

ALIAS = json.load(open("alias_map.json"))
def entity(name):
    for rx, ent in ALIAS.items():
        if re.match(rx, name, re.I): return ent, True
    return name, False

seen = collections.defaultdict(set)   # (app, entity) -> methods
names = collections.defaultdict(set)
for f in ("static_detected.csv", "dynamic_detected.csv"):
    for r in csv.DictReader(open(f)):
        e, _ = entity(r["sdk"]); seen[(r["app"], e)].add(r["method"]); names[(r["app"], e)].add(r["sdk"])
rows = []
for (app, e), m in sorted(seen.items()):
    by = "both" if len(m) == 2 else next(iter(m))
    rows.append([app, e, "; ".join(sorted(names[(app, e)])), by])
csv.writer(open("ground_truth.csv", "w", newline="")).writerows(
    [["app", "third_party_entity", "raw_names", "detected_by"]] + rows)
c = collections.Counter(r[3] for r in rows)
json.dump({"pairs": len(rows), "by_method": c}, open("resolution_stats.json", "w"), indent=1)
print(len(rows), "app-thirdparty pairs;", dict(c))
