import json
import logging
import os
import time
from datetime import datetime

import requests

try:
    import matplotlib.pyplot as plt

    MATPLOTLIB_AVAILABLE = True
except ImportError:
    MATPLOTLIB_AVAILABLE = False

# Configure logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

GATEWAY_URL = "http://localhost:8080"
# Service names mapping for fault injection via Gateway
SERVICES = {"account": "account", "payment": "payment", "notification": "notification"}


class ExperimentOrchestrator:
    def __init__(self):
        self.results = []

    def get_health(self, service_name):
        # Check health via Actuator directly if possible, or via gateway
        # In this env, ports are exposed: 8082, 8081, 8085
        ports = {"account": 8082, "payment": 8081, "notification": 8085}
        try:
            resp = requests.get(
                f"http://localhost:{ports[service_name]}/actuator/health", timeout=2
            )
            return resp.status_code == 200
        except:
            return False

    def inject_fault(self, service, fault_endpoint, payload=None):
        url = f"{GATEWAY_URL}/fault/{service}/{fault_endpoint}"
        logger.info(f"Injecting {fault_endpoint} into {service}...")
        try:
            if payload:
                resp = requests.post(url, json=payload)
            else:
                resp = requests.post(url)
            return resp.status_code == 200
        except Exception as e:
            logger.error(f"Failed to inject fault: {e}")
            return False

    def clear_faults(self, service):
        logger.info(f"Clearing faults for {service}...")
        self.inject_fault(service, "error-rate", {"rate": 0})
        self.inject_fault(service, "unresponsive", {"enable": False})
        self.inject_fault(service, "memory-leak", {"enable": False})
        self.inject_fault(service, "cpu-spike", {"enable": False})

    def run_scenario(self, name, service, fault_type, payload=None):
        logger.info(f"--- Starting Scenario: {name} ---")

        # Get current number of lines in recovery-engine logs to only look at new logs
        log_cmd = f"docker logs are-recovery-engine 2>/dev/null | wc -l"
        try:
            baseline_lines = int(os.popen(log_cmd).read().strip())
        except:
            baseline_lines = 0

        t_inject = time.time()

        if fault_type == "crash":
            success = self.inject_fault(service, "crash")
        else:
            success = self.inject_fault(service, fault_type, payload)

        if not success:
            logger.error(f"Scenario {name} aborted: fault injection failed")
            return

        # Poll for restoration by reading the recovery engine logs
        logger.info("Waiting for ARE to detect and recover...")
        t_recover = None

        max_wait = 180  # 3 mins
        start_poll = time.time()
        while time.time() - start_poll < max_wait:
            try:
                # Tail the logs after baseline to find Executing action on service
                tail_cmd = f"docker logs are-recovery-engine | tail -n +{baseline_lines + 1} | grep 'Executing .* on service {service}'"
                output = os.popen(tail_cmd).read().strip()
                if output:
                    logger.info(f"ARE Action detected: {output}")
                    t_recover = time.time()
                    break
            except Exception as e:
                pass
            time.sleep(3)

        mttr = t_recover - t_inject if t_recover else -1
        result = {
            "scenario": name,
            "service": service,
            "t_inject": datetime.fromtimestamp(t_inject).isoformat(),
            "t_recover": datetime.fromtimestamp(t_recover).isoformat()
            if t_recover
            else "FAILED",
            "mttr_seconds": mttr,
        }
        self.results.append(result)
        if mttr >= 0:
            logger.info(f"Scenario {name} finished. MTTR: {mttr:.2f}s")
        else:
            logger.error(f"Scenario {name} FAILED to recover within {max_wait}s")

        # Cleanup
        if fault_type != "crash":
            self.clear_faults(service)
        else:
            # Crash needs a restart, which Spring Boot Admin should handle (stubbed)
            # In our simulation, we must start it again manually via make start-service in a real setup
            # But since it's docker and osascript, wait a bit
            pass
        time.sleep(20)  # wait before next scenario to let alerts clear

    def generate_graph(self):
        if not MATPLOTLIB_AVAILABLE:
            logger.warning("Matplotlib not installed. Skipping graph generation.")
            return

        scenarios = [r["scenario"] for r in self.results]
        mttrs = [r["mttr_seconds"] for r in self.results]

        plt.figure(figsize=(10, 6))
        bars = plt.bar(
            scenarios,
            mttrs,
            color=["#4C72B0", "#55A868", "#C44E52", "#8172B2", "#CCB974", "#64B5CD"][
                : len(scenarios)
            ],
        )
        plt.title("Auto-Recovery Engine - Phase 4 MTTR by Scenario")
        plt.xlabel("Failure Scenario")
        plt.ylabel("MTTR (Seconds)")
        plt.xticks(rotation=45, ha="right")

        for bar in bars:
            yval = bar.get_height()
            if yval >= 0:
                plt.text(
                    bar.get_x() + bar.get_width() / 2,
                    yval + 0.5,
                    f"{yval:.1f}s",
                    ha="center",
                    va="bottom",
                )
            else:
                plt.text(
                    bar.get_x() + bar.get_width() / 2,
                    0.5,
                    "FAILED",
                    ha="center",
                    va="bottom",
                    color="red",
                )

        plt.tight_layout()
        plt.savefig("phase4_mttr_results.png")
        logger.info("Generated graph: phase4_mttr_results.png")

    def save_results(self):
        with open("phase4_results.json", "w") as f:
            json.dump(self.results, f, indent=2)
        logger.info("Results saved to phase4_results.json")
        self.generate_graph()


def main():
    orch = ExperimentOrchestrator()

    # 1. Service Crash
    orch.run_scenario("Service Crash", "account", "crash")

    # 2. High Error Rate
    orch.run_scenario("High Error Rate", "payment", "error-rate", {"rate": 100})

    # 3. Response Time Degradation (Latency Spike)
    orch.run_scenario("Latency Spike", "payment", "unresponsive", {"enable": True})

    # 4. Resource Exhaustion
    orch.run_scenario(
        "Resource Exhaustion", "notification", "cpu-spike", {"enable": True}
    )

    # 5. Memory Leak
    orch.run_scenario("Memory Leak", "account", "memory-leak", {"enable": True})

    # 6. Cascading Failure
    # Simulating cascading by making account unresponsive which impacts gateway and others
    orch.run_scenario("Cascading Failure", "account", "unresponsive", {"enable": True})

    orch.save_results()


if __name__ == "__main__":
    main()
