# WorldGen container for Saigon.Quest
# Base: Python 3.11 as required by WorldGen
FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    DEBIAN_FRONTEND=noninteractive \
    HUGGINGFACE_HUB_CACHE=/root/.cache/huggingface \
    HF_HOME=/root/.cache/huggingface \
    FORCE_CUDA=0

# System dependencies often required by ML/graphics stacks
RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    ffmpeg \
    libgl1 \
    libglib2.0-0 \
    ca-certificates \
    curl \
    build-essential \
    cmake \
    ninja-build \
    pkg-config \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt

# Clone WorldGen
RUN git clone https://github.com/ZiYang-xie/WorldGen.git /opt/WorldGen

WORKDIR /opt/WorldGen

# Upgrade pip, install CUDA-enabled PyTorch (cu121) and build PyTorch3D from source (CPU-only) to avoid external wheel URLs
RUN python -m pip install --upgrade pip setuptools wheel && \
    pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cu121 \
        torch==2.1.0 torchvision==0.16.0 torchaudio==2.1.0 && \
    pip install --no-cache-dir "git+https://github.com/facebookresearch/pytorch3d.git@v0.7.8" && \
    pip install --no-cache-dir open3d pillow && \
    if [ -f requirements.txt ]; then pip install -r requirements.txt; fi || true

# If WorldGen is a package, install it as editable (best effort)
RUN if [ -f setup.py ] || [ -f pyproject.toml ]; then pip install -e . || true; fi

# Copy our runner, shim, and entrypoint
WORKDIR /
COPY worldgen_runner.py /opt/worldgen_runner.py
COPY pytorch3d_shim /opt/pytorch3d_shim
COPY entrypoint.sh /usr/local/bin/worldgen-entrypoint
RUN chmod +x /usr/local/bin/worldgen-entrypoint

# Workspace for host binding
WORKDIR /workspace

ENTRYPOINT ["worldgen-entrypoint"]
