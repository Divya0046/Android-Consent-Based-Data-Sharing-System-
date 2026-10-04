import csv
import json
import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "results")


def read_csv(path):
    with open(path) as f:
        return list(csv.DictReader(f))


# 1. Scalability: policy count vs latency
rows = read_csv(os.path.join(OUT, "scalability.csv"))
x = [int(r["policy_count"]) for r in rows]
mean_y = [float(r["mean_latency_us"]) for r in rows]
median_y = [float(r["median_latency_us"]) for r in rows]
max_y = [float(r["max_latency_us"]) for r in rows]

plt.figure(figsize=(6, 4))
plt.plot(x, mean_y, marker="o", label="Mean")
plt.plot(x, median_y, marker="s", label="Median")
plt.plot(x, max_y, marker="^", label="Max")
plt.xscale("log")
plt.xlabel("Number of stored consent policies (log scale)")
plt.ylabel("Validation latency (µs)")
plt.title("Policy Validation Latency vs. Policy Store Size")
plt.legend()
plt.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig(os.path.join(OUT, "chart_scalability.png"), dpi=160)
plt.close()

# 2. Storage overhead
rows = read_csv(os.path.join(OUT, "storage_overhead.csv"))
x = [int(r["policy_count"]) for r in rows]
y = [float(r["storage_kb"]) for r in rows]
plt.figure(figsize=(6, 4))
plt.plot(x, y, marker="o", color="tab:green")
plt.xscale("log")
plt.xlabel("Number of stored consent policies (log scale)")
plt.ylabel("Storage size (KB)")
plt.title("Consent Policy Storage Overhead")
plt.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig(os.path.join(OUT, "chart_storage.png"), dpi=160)
plt.close()

# 3. Model comparison bar chart (accuracy / false-allow / false-deny)
with open(os.path.join(OUT, "model_comparison_detail.json")) as f:
    comp = json.load(f)

labels = [c["model"].split("(")[0].split(":")[0].strip() for c in comp]
acc = [c["authorization_accuracy_pct"] for c in comp]
fa = [c["false_allow_rate_pct"] for c in comp]
fd = [c["false_deny_rate_pct"] for c in comp]

import numpy as np
x = np.arange(len(labels))
width = 0.25
plt.figure(figsize=(7, 4.5))
plt.bar(x - width, acc, width, label="Accuracy %")
plt.bar(x, fa, width, label="False-Allow Rate %")
plt.bar(x + width, fd, width, label="False-Deny Rate %")
plt.xticks(x, labels, rotation=10)
plt.ylabel("Percent")
plt.title("Proposed Framework vs. Baseline Consent Models")
plt.legend()
plt.grid(True, axis="y", alpha=0.3)
plt.tight_layout()
plt.savefig(os.path.join(OUT, "chart_model_comparison.png"), dpi=160)
plt.close()

# 4. Latency distribution histogram
rows = read_csv(os.path.join(OUT, "latency_raw_us.csv"))
lat = [float(r["latency_us"]) for r in rows]
plt.figure(figsize=(6, 4))
plt.hist(lat, bins=50, color="tab:purple", alpha=0.8)
plt.xlabel("Validation latency (µs)")
plt.ylabel("Frequency")
plt.title("Distribution of Policy Validation Latency (500 stored policies)")
plt.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig(os.path.join(OUT, "chart_latency_distribution.png"), dpi=160)
plt.close()

# 5. Policy generation overhead vs number of sharedData entries
rows = read_csv(os.path.join(OUT, "policy_generation.csv"))
x = [int(r["shared_data_entries"]) for r in rows]
y = [float(r["mean_generation_latency_us"]) for r in rows]
pts = sorted(zip(x, y))
x = [p[0] for p in pts]
y = [p[1] for p in pts]
plt.figure(figsize=(6, 4))
plt.scatter(x, y, alpha=0.6, color="tab:orange")
plt.xlabel("Number of declared sharedData entries")
plt.ylabel("Policy generation latency (µs)")
plt.title("Consent Policy Generation Overhead")
plt.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig(os.path.join(OUT, "chart_policy_generation.png"), dpi=160)
plt.close()

print("Charts written to", OUT)
