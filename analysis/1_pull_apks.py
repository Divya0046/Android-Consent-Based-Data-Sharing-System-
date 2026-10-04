"""Windows-friendly APK puller. Needs adb in PATH and USB debugging ON.
Usage: python 1_pull_apks.py   -> apks/<package>/*.apk and apks/versions.csv"""
import csv, hashlib, os, re, subprocess, sys

def adb(*a):
    r = subprocess.run(["adb", *a], capture_output=True, text=True)
    return r.stdout.replace("\r", "")

if "device" not in adb("devices").split("\n", 1)[1]:
    sys.exit("No device found. Check USB debugging / 'Allow' popup on phone, then run 'adb devices'.")

pkgs = [r["package"] for r in csv.DictReader(open("apps.csv"))]
os.makedirs("apks", exist_ok=True)
rows = [["package", "version", "sha256_base"]]
for pkg in pkgs:
    paths = re.findall(r"package:(\S+)", adb("shell", "pm", "path", pkg))
    if not paths:
        print("NOT INSTALLED:", pkg); continue
    d = os.path.join("apks", pkg); os.makedirs(d, exist_ok=True)
    for p in paths:
        subprocess.run(["adb", "pull", p, d], capture_output=True)
    m = re.search(r"versionName=(\S+)", adb("shell", "dumpsys", "package", pkg))
    base = os.path.join(d, "base.apk")
    sha = hashlib.sha256(open(base, "rb").read()).hexdigest() if os.path.exists(base) else "n/a"
    rows.append([pkg, m.group(1) if m else "?", sha])
    print("pulled", pkg, len(os.listdir(d)), "files")
csv.writer(open("apks/versions.csv", "w", newline="")).writerows(rows)
