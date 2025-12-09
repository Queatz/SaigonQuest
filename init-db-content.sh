#!/usr/bin/env bash
# Initialize the content (uploads) ArangoDB on the remote server by running the setup script there.
# This uploads setup_arango_content_db.sh and executes it over SSH.
#
# Usage:
#   ./init-db-content.sh
#
# Environment overrides:
#   HOST                 - SSH hostname (default: content.saigon.quest)
#   SSH_USER             - SSH user (default: root)
#   SSH_KEY              - SSH private key path (default: $HOME/.ssh/id_rsa)
#   CONTENT_DEPLOY_DIR   - Remote directory to upload/run the script (default: /root/saigonquest-content)

set -euo pipefail

HOST="${HOST:-content.saigon.quest}"
# Keep in sync with deploy/run scripts that currently override to the direct IP
HOST="38.54.15.228"
SSH_USER="${SSH_USER:-root}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/id_rsa}"
DEST_DIR="${CONTENT_DEPLOY_DIR:-/root/saigonquest-content}"
LOCAL_SCRIPT="setup_arango_content_db.sh"
REMOTE_SCRIPT="$DEST_DIR/$LOCAL_SCRIPT"

if [[ ! -f "$LOCAL_SCRIPT" ]]; then
  echo "Local script not found: $LOCAL_SCRIPT"
  exit 1
fi

echo "Initializing content ArangoDB on $SSH_USER@$HOST ..."

# Ensure remote directory exists
ssh -o BatchMode=yes -i "$SSH_KEY" "$SSH_USER@$HOST" "mkdir -p '$DEST_DIR'"

# Upload the setup script
scp -o BatchMode=yes -i "$SSH_KEY" "$LOCAL_SCRIPT" "$SSH_USER@$HOST:$REMOTE_SCRIPT"

# Execute the script remotely
ssh -o BatchMode=yes -i "$SSH_KEY" "$SSH_USER@$HOST" "chmod +x '$REMOTE_SCRIPT' && bash '$REMOTE_SCRIPT'"

echo "Content ArangoDB initialization completed."
