#!/usr/bin/env bash
# Deploy the content server to the remote server via SSH.
# Usage: ./deploy-content.sh [-b]
#   -b  Run the content build task before deploying

set -euo pipefail

HOST="content.saigon.quest"
HOST="38.54.15.228"
SSH_USER="root"
SSH_KEY="$HOME/.ssh/id_rsa"
DEST_DIR="${CONTENT_DEPLOY_DIR:-/root/saigonquest-content}"
GRADLE_TASK=":content:shadowJar"
ARTIFACT_DIR="content/build/libs"

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

echo "Deploying content server to $SSH_USER@$HOST ..."

if $BUILD_FIRST; then
  echo "Running build: ./gradlew $GRADLE_TASK"
  ./gradlew $GRADLE_TASK
fi

if [[ ! -d "$ARTIFACT_DIR" ]]; then
  echo "Content build output not found at $ARTIFACT_DIR. Use -b to build first or check your build."
  fail
fi

# Prefer *-all.jar, otherwise pick the most recent jar
JAR_FILE="$(ls -1t "$ARTIFACT_DIR"/*-all.jar 2>/dev/null | head -n1 || true)"
if [[ -z "${JAR_FILE}" ]]; then
  JAR_FILE="$(ls -1t "$ARTIFACT_DIR"/*.jar 2>/dev/null | head -n1 || true)"
fi

if [[ -z "${JAR_FILE}" ]]; then
  echo "No content jar found in $ARTIFACT_DIR."
  fail
fi

echo "Ensuring remote directory exists: $DEST_DIR"
ssh -o BatchMode=yes -i "$SSH_KEY" "$SSH_USER@$HOST" "mkdir -p '$DEST_DIR'"

echo "Uploading $JAR_FILE to remote host..."
scp -o BatchMode=yes -i "$SSH_KEY" "$JAR_FILE" "$SSH_USER@$HOST:$DEST_DIR/app.jar.tmp"

# Atomic replace on remote
ssh -o BatchMode=yes -i "$SSH_KEY" "$SSH_USER@$HOST" "mv '$DEST_DIR/app.jar.tmp' '$DEST_DIR/app.jar'"

success
