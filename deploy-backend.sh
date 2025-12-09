#!/usr/bin/env bash
# Deploy the backend to the remote server via SSH.
# Usage: ./deploy-backend.sh [-b]
#   -b  Run the backend build task before deploying

set -euo pipefail

HOST="api.saigon.quest"
HOST="156.244.25.6"
SSH_USER="root"
SSH_KEY="$HOME/.ssh/id_rsa"
DEST_DIR="${BACKEND_DEPLOY_DIR:-/root/saigonquest-backend}"
GRADLE_TASK=":backend:shadowJar"
ARTIFACT_DIR="backend/build/libs"

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

echo "Deploying backend to $SSH_USER@$HOST ..."

if $BUILD_FIRST; then
  echo "Running build: ./gradlew $GRADLE_TASK"
  ./gradlew $GRADLE_TASK
fi

if [[ ! -d "$ARTIFACT_DIR" ]]; then
  echo "Backend build output not found at $ARTIFACT_DIR. Use -b to build first or check your build."
  fail
fi

# Prefer *-all.jar, otherwise pick the most recent jar
JAR_FILE="$(ls -1t "$ARTIFACT_DIR"/*-all.jar 2>/dev/null | head -n1 || true)"
if [[ -z "${JAR_FILE}" ]]; then
  JAR_FILE="$(ls -1t "$ARTIFACT_DIR"/*.jar 2>/dev/null | head -n1 || true)"
fi

if [[ -z "${JAR_FILE}" ]]; then
  echo "No backend jar found in $ARTIFACT_DIR."
  fail
fi

echo "Ensuring remote directory exists: $DEST_DIR"
ssh -o BatchMode=yes -i "$SSH_KEY" "$SSH_USER@$HOST" "mkdir -p '$DEST_DIR'"

echo "Uploading $JAR_FILE to remote host..."
scp -o BatchMode=yes -i "$SSH_KEY" "$JAR_FILE" "$SSH_USER@$HOST:$DEST_DIR/app.jar.tmp"

# Atomic replace on remote
ssh -o BatchMode=yes -i "$SSH_KEY" "$SSH_USER@$HOST" "mv '$DEST_DIR/app.jar.tmp' '$DEST_DIR/app.jar'"

success
