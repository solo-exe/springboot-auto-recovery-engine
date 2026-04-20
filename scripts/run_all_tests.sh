#!/bin/bash

# =============================================================================
# Master Test Orchestrator for Auto Recovery Engine (Phase 4)
# =============================================================================
# Assumes 'make launch' has already been successfully run.
# Integrates `uv` for python virtual environment.

set -e

echo "====================================================================="
echo "Phase 4 Master Test Runner"
echo "====================================================================="

export PATH="$HOME/.local/bin:$HOME/.cargo/bin:$PATH:/usr/local/bin:/usr/bin:/bin"

# 1. Ensure `uv` is installed
if ! command -v uv &> /dev/null; then
    echo "📦 'uv' not found. Installing into local binaries..."
    curl -LsSf https://astral.sh/uv/install.sh | sh
    export PATH="$HOME/.cargo/bin:$PATH"
else
    echo "✅ 'uv' already installed."
fi

# 2. Setup Virtual Environment
echo "🐍 Setting up Python Virtual Environment..."
uv venv --clear .venv
source .venv/bin/activate
uv pip install requests matplotlib

# 3. Run Observability Test
echo ""
echo "🚀 [Step 1] Running Observability Integration Script"
chmod +x scripts/test-observability.sh
./scripts/test-observability.sh

# 4. Starting Background Simulation
echo ""
echo "🚗 [Step 2] Launching Background Load Simulation"
uv run python scripts/simulate_load.py &
SIM_PID=$!
echo "   Simulation running in background (PID: $SIM_PID)"

# Wait for simulation to initiate
sleep 10

# 5. Run Experiments
echo ""
echo "🧪 [Step 3] Executing Phase 4 Resilience Experiments"
uv run python scripts/run_experiments.py

# 6. Cleanup
echo ""
echo "🧹 [Step 4] Cleaning up Background Tasks"
kill $SIM_PID || true

echo ""
echo "🎉 All Tests Completed!"
echo "➡️  Check 'phase4_results.json' and 'phase4_mttr_results.png' for experiment outcomes."
