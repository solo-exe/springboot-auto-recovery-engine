import glob
import json
import statistics
import sys


def compute_stats(file_path):
    try:
        with open(file_path, "r") as f:
            data = json.load(f)

        metrics_dict = {
            "detection_latency_ms": [],
            "execution_latency_ms": [],
            "mttr_ms": [],
            "cpu_overhead_percent": [],
            "memory_overhead_mb": [],
        }

        for run in data:
            for key, value in run.items():
                if isinstance(value, dict) and "metrics" in value:
                    metrics = value["metrics"]
                    overhead = value.get("overhead", {})

                    if "detection_latency_ms" in metrics:
                        metrics_dict["detection_latency_ms"].append(
                            metrics["detection_latency_ms"]
                        )
                    if "execution_latency_ms" in metrics:
                        metrics_dict["execution_latency_ms"].append(
                            metrics["execution_latency_ms"]
                        )
                    if "mttr_ms" in metrics:
                        metrics_dict["mttr_ms"].append(metrics["mttr_ms"])

                    if "recovery_engine_cpu_percent" in overhead:
                        metrics_dict["cpu_overhead_percent"].append(
                            overhead["recovery_engine_cpu_percent"]
                        )
                    if "recovery_engine_mem_mb" in overhead:
                        metrics_dict["memory_overhead_mb"].append(
                            overhead["recovery_engine_mem_mb"]
                        )

        return metrics_dict
    except Exception as e:
        print(f"Error reading {file_path}: {e}")
        return None


files = glob.glob("logs/*_results.json")
print("Statistics summary:")
for file in files:
    print(f"\n--- {file} ---")
    metrics_dict = compute_stats(file)
    if metrics_dict:
        for metric, values in metrics_dict.items():
            if not values:
                continue
            mean_val = statistics.mean(values)
            median_val = statistics.median(values)
            std_val = statistics.stdev(values) if len(values) > 1 else 0.0
            min_val = min(values)
            max_val = max(values)
            count = len(values)
            print(
                f"{metric}: Mean={mean_val:.2f}, Median={median_val:.2f}, SD={std_val:.2f}, Min={min_val}, Max={max_val}, N={count}"
            )
