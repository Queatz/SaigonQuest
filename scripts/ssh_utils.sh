#!/usr/bin/env bash
# Utilities for SSH-related operations shared by deploy/run scripts.

# Do NOT use set -euo pipefail here because this file is sourced by other scripts
# that already manage their own shell options.

# Ensure ~/.ssh directory and known_hosts file exist with safe permissions
ensure_ssh_prereqs() {
  local ssh_dir
  ssh_dir="${HOME}/.ssh"
  mkdir -p "$ssh_dir" 2>/dev/null || true
  chmod 700 "$ssh_dir" 2>/dev/null || true

  local kh_file
  kh_file="${KNOWN_HOSTS_FILE:-${ssh_dir}/known_hosts}"
  if [ ! -f "$kh_file" ]; then
    touch "$kh_file" 2>/dev/null || true
  fi
  chmod 600 "$kh_file" 2>/dev/null || true
}

# Ensure the given host is present in known_hosts. Optionally pass a port as $2.
ensure_known_host() {
  local host="$1"
  local port="${2:-22}"
  local kh_file
  kh_file="${KNOWN_HOSTS_FILE:-${HOME}/.ssh/known_hosts}"

  # Prepare dirs/files
  ensure_ssh_prereqs

  # Check if host already present
  if ! ssh-keygen -F "$host" -f "$kh_file" >/dev/null 2>&1; then
    echo "Adding $host to known_hosts..." >&2
    if command -v ssh-keyscan >/dev/null 2>&1; then
      ssh-keyscan -p "$port" -H "$host" >> "$kh_file" 2>/dev/null || true
      # Optional: show the entry we just added
      ssh-keygen -F "$host" -f "$kh_file" -l >/dev/null 2>&1 || true
    else
      echo "Warning: ssh-keyscan not found; skipping known_hosts prefill for $host" >&2
    fi
  fi
}
