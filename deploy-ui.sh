#!/usr/bin/env bash
# Deploy the UI to the remote server via SSH.
# Usage: ./deploy-ui.sh [-b]
#   -b  Run the UI build task before deploying

set -euo pipefail

HOST="saigon.quest"
HOST="154.205.142.48"
SSH_USER="root"
SSH_KEY="$HOME/.ssh/id_rsa"
DEST_DIR="${UI_DEPLOY_DIR:-/root/saigonquest-ui}"
GRADLE_TASK=":prodApp"
LOCAL_BUILD_DIR="build/dist/js/productionExecutable"

print_usage() {
  echo "Usage: $0 [-b]"
}

BUILD_FIRST=false
while getopts ":b" opt; do
  case "$opt" in
    b)
      BUILD_FIRST=true
      ;;
    ?)
      print_usage
      exit 2
      ;;
  esac
done

fail() {
  echo "Failure"
  exit 1
}

success() {
  echo "Success"
  exit 0
}

trap 'fail' ERR

echo "Deploying UI to $SSH_USER@$HOST ..."

if $BUILD_FIRST; then
  echo "Running build: ./gradlew $GRADLE_TASK"
  ./gradlew $GRADLE_TASK
fi

if [[ ! -d "$LOCAL_BUILD_DIR" ]]; then
  echo "UI build output not found at $LOCAL_BUILD_DIR. Use -b to build first or check your build."
  fail
fi

echo "Ensuring remote directory exists: $DEST_DIR"
ssh -o BatchMode=yes -i "$SSH_KEY" "$SSH_USER@$HOST" "mkdir -p '$DEST_DIR'"

echo "Syncing UI files to remote host..."
rsync -az --delete -e "ssh -i $SSH_KEY -o BatchMode=yes" "$LOCAL_BUILD_DIR/" "$SSH_USER@$HOST:$DEST_DIR/"

success
