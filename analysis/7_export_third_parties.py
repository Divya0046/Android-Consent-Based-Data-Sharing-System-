"""Regenerates the Android asset from ground_truth.csv after any re-run.
Usage: python 7_export_third_parties.py  -> third_parties.json (copy into image-preview/src/main/assets/)"""
import csv, json, collections
FIRST_PARTY = {("com.facebook.orca", "Meta"), ("com.google.android.apps.messaging", "Google")}
out = collections.defaultdict(list)
for r in csv.DictReader(open("apps.csv")): out[r["package"]]
for r in csv.DictReader(open("ground_truth.csv")):
    out[r["app"]].append({"name": r["third_party_entity"], "detectedBy": r["detected_by"],
                          "firstParty": (r["app"], r["third_party_entity"]) in FIRST_PARTY})
json.dump(out, open("third_parties.json", "w"), indent=1)
print({k: len(v) for k, v in out.items()})
