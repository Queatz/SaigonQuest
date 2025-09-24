#!/usr/bin/env bash
set -euo pipefail

# If CPU mode is explicitly requested by the wrapper, disable CUDA visibility.
if [[ "${WORLDGEN_FORCE_CPU:-}" == "1" ]]; then
  export CUDA_VISIBLE_DEVICES=""
fi

python /opt/worldgen_runner.py "$@"
