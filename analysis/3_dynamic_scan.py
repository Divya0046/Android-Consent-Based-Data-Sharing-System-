"""Dynamic domains -> SDKs. Input: PCAPdroid CSV exports, one per app, saved as pcap/<package>.csv
(PCAPdroid > Connections > menu > Export CSV, filter to the app first).
Usage: python3 3_dynamic_scan.py   -> dynamic_detected.csv, unmapped_domains.csv"""
import csv, glob, json, os, re

t = json.load(open("trackers.json"))
trackers = list(t["trackers"].values()) if "trackers" in t else list(t.values())
nets = [(x["name"], x.get("website", ""), re.compile(x["network_signature"]))
        for x in trackers if x.get("network_signature")]
rows, unmapped = [], []
for f in sorted(glob.glob("pcap/*.csv")):
    pkg = os.path.basename(f)[:-4]
    hosts = set()
    for r in csv.DictReader(open(f, newline="", encoding="utf-8", errors="ignore")):
        for col in ("Server Name", "Info", "Host", "Dst"):
            v = (r.get(col) or "").strip().lower()
            if v and re.search(r"[a-z]", v) and "." in v:
                hosts.add(v.split(" ")[0]); break
    for h in sorted(hosts):
        hit = [(n, s) for n, s, rx in nets if rx.search(h)]
        for n, s in hit: rows.append([pkg, n, s, "dynamic", h])
        if not hit: unmapped.append([pkg, h])
    print(pkg, "domains:", len(hosts))
csv.writer(open("dynamic_detected.csv", "w", newline="")).writerows(
    [["app", "sdk", "website", "method", "evidence"]] + rows)
csv.writer(open("unmapped_domains.csv", "w", newline="")).writerows([["app", "domain"]] + unmapped)
