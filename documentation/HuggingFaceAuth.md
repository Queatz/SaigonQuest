# Using Hugging Face with WorldGen (HUGGINGFACE_HUB_TOKEN)

Some of WorldGen’s dependencies download models from Hugging Face, including gated models such as black-forest-labs/FLUX.1-dev. If a model is gated, you must authenticate and have access to it.

This repo’s WorldGen wrapper already supports Hugging Face tokens and caches. Follow these steps.

## 1) Make sure your HF account can access the model

- Visit the model page (e.g. https://huggingface.co/black-forest-labs/FLUX.1-dev) and accept its terms with your account.
- Your token must belong to the same account that accepted the terms.

## 2) Provide your token to the wrapper (choose ONE)

Option A — Environment variable (recommended for CI and one-off runs)
- Linux/macOS (bash/zsh):
  export HUGGINGFACE_HUB_TOKEN=hf_xxx
  # or
  export HF_TOKEN=hf_xxx

- Windows (PowerShell):
  $Env:HUGGINGFACE_HUB_TOKEN = "hf_xxx"
  # or
  $Env:HF_TOKEN = "hf_xxx"

Option B — Login with the CLI on your host (creates a token file we auto-mount)
- Install the CLI (if needed): python -m pip install -U huggingface_hub
- Log in: huggingface-cli login
- This writes a token file to one of:
  - ~/.cache/huggingface/token (preferred)
  - ~/.huggingface/token
  - ~/.config/huggingface/token

The wrapper automatically:
- Passes HUGGINGFACE_HUB_TOKEN/HF_TOKEN into the container if set.
- Mounts the token file read-only from the above locations if present.
- Sets HUGGINGFACE_HUB_CACHE and HF_HOME inside the container so the cache persists.

## 3) Run WorldGen

From the repo root:
- Generate from text:
  ./scripts/worldgen -p "A pretty beach scene with pines and pink and white flowers"

- Generate from 360° photo:
  ./scripts/worldgen -i "/absolute/path/to/panorama.jpg"

The first run builds the Docker image and may download large model files. Subsequent runs reuse the cache.

## 4) Troubleshooting

- GatedRepoError: Ensure the token is valid, you’re logged in to the right HF account, and you accepted the model’s terms. Re-run with your token exported or with the CLI login.
- Using the wrong shell: On macOS/Linux use `export VAR=...`; on Windows PowerShell use `$Env:VAR = "..."`.
- Token scope: A read (inference) token is usually sufficient.
- Verify token detection: The wrapper prints a message like "[worldgen] Using HUGGING FACE_HUB_TOKEN from environment" or "Mounted Hugging Face token file from host".
- Clear/refresh token: Delete the token file at ~/.cache/huggingface/token (or other path) and run `huggingface-cli login` again, or export a new token.

## Security notes

- Treat your token as a secret. Prefer environment variables or the HF CLI token file over hardcoding.
- Revoke tokens in your HF account settings if they’re ever exposed.
