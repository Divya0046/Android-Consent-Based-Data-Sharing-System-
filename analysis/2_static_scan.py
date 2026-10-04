"""Static SDK detection. Matches class descriptors in every classes*.dex of every APK
(base + splits) against Exodus Privacy tracker code signatures. No third-party libs needed.
Usage: python3 2_static_scan.py            -> static_detected.csv"""
import csv, glob, json, os, re, urllib.request, zipfile

API = "https://reports.exodus-privacy.eu.org/api/trackers"
def load_trackers():
    if not os.path.exists("trackers.json"):
        urllib.request.urlretrieve(API, "trackers.json")   # or download in browser and save as trackers.json
    t = json.load(open("trackers.json"))
    return list(t["trackers"].values()) if "trackers" in t else list(t.values())

DESC = re.compile(rb"L[A-Za-z0-9_$/]{3,200};")
def classes_of(apk):
    out = set()
    with zipfile.ZipFile(apk) as z:
        for n in z.namelist():
            if re.fullmatch(r"classes\d*\.dex", n):
                for m in DESC.findall(z.read(n)):
                    out.add(m[1:-1].decode().replace("/", "."))
    return out

trackers = [(t["name"], t.get("website", ""), re.compile(t["code_signature"]))
            for t in load_trackers() if t.get("code_signature")]
rows = []
for d in sorted(glob.glob("apks/*/")):
    pkg = os.path.basename(os.path.normpath(d)); cls = set()
    for apk in glob.glob(d + "*.apk"):
        cls |= classes_of(apk)
    joined = "\n".join(sorted(cls))
    for name, site, rx in trackers:
        m = rx.search(joined)
        if m:
            rows.append([pkg, name, site, "static", m.group(0)[:60]])
    print(pkg, "classes:", len(cls), "SDKs:", sum(1 for r in rows if r[0] == pkg))
csv.writer(open("static_detected.csv", "w", newline="")).writerows(
    [["app", "sdk", "website", "method", "evidence"]] + rows)
