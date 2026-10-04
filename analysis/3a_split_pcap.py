"""Splits combined PCAPdroid CSV export(s) into pcap/<package>.csv (one file per app in apps.csv).
Put your export(s) in a folder 'pcap_raw' (any file names). Usage: python 3a_split_pcap.py"""
import csv, glob, os, collections

apps = {r["package"] for r in csv.DictReader(open("apps.csv"))}
os.makedirs("pcap", exist_ok=True)
for old in glob.glob("pcap/*.csv"):
    try: os.remove(old)
    except PermissionError: raise SystemExit("Close Excel (file open): " + old + " -- close it, then run again")
rows = collections.defaultdict(list); header = None
for f in glob.glob("pcap_raw/*.csv"):
    rd = csv.reader(open(f, newline="", encoding="utf-8", errors="ignore"))
    for line in rd:                      # PCAPdroid may put comment lines before header
        if line and any(h.strip().lower() in ("info", "ipproto") for h in line):
            header = line; break
    if not header: print("no header in", f); continue
    pk = next((i for i, h in enumerate(header) if "package" in h.lower()), None)
    for line in rd:
        if len(line) < len(header): continue
        if pk is None:                   # fallback: find the column holding a known package name
            pk = next((i for i, v in enumerate(line) if v in apps), None)
            if pk is None: continue
        rows[line[pk]].append(line)
for pkg, r in sorted(rows.items()):
    tag = "" if pkg in apps else "   (not in apps.csv, skipped)"
    print(pkg, len(r), "connections", tag)
    if pkg in apps:
        w = csv.writer(open(f"pcap/{pkg}.csv", "w", newline="")); w.writerow(header); w.writerows(r)
print("missing (no traffic captured):", sorted(apps - set(rows)))
