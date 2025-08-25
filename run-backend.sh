#!/usr/bin/env bash
# Run the backend .jar on the remote server via SSH using nohup.
# Defaults target the production backend host and directory.
#
# Usage:
#   ./run-backend.sh
#
# Environment overrides:
#   HOST           - SSH hostname (default: api.saigon.quest)
#   SSH_USER       - SSH user (default: root)
#   SSH_KEY        - SSH private key path (default: $HOME/.ssh/id_rsa)
#   BACKEND_DEPLOY_DIR - Remote directory containing app.jar (default: /root/saigonquest-backend)
#   JAVA_OPTS      - Extra Java options (e.g., "-Xms256m -Xmx512m")
#   REMOTE_ENV     - Space-separated env vars to prefix the command (e.g., "PORT=8080 PROFILE=prod")
#   LOG_DIR        - Remote log directory (default: $BACKEND_DEPLOY_DIR/logs)

set -euo pipefail

HOST="${HOST:-api.saigon.quest}"
HOST="156.244.25.6"
SSH_USER="${SSH_USER:-root}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/id_rsa}"
DEST_DIR="${BACKEND_DEPLOY_DIR:-/root/saigonquest-backend}"
JAVA_OPTS="${JAVA_OPTS:-}"
LOG_DIR="${LOG_DIR:-$DEST_DIR/logs}"
PID_FILE="$DEST_DIR/app.pid"
JAR="$DEST_DIR/app.jar"
REMOTE_ENV="${REMOTE_ENV:-}"

echo "Running backend on $SSH_USER@$HOST ..."

ssh -o BatchMode=yes -i "$SSH_KEY" "$SSH_USER@$HOST" "set -eu; \
  mkdir -p \"$DEST_DIR\" \"$LOG_DIR\"; \
  if [ -f \"$PID_FILE\" ] && kill -0 \$(cat \"$PID_FILE\") 2>/dev/null; then \
    echo 'Stopping existing backend process (PID ' \$(cat \"$PID_FILE\") ')...'; \
    kill \$(cat \"$PID_FILE\") || true; \
    sleep 2; \
  fi; \
  LOG_FILE=$LOG_DIR/app-\$(date +%Y%m%d-%H%M%S).log; \
  echo 'Starting backend: nohup java $JAVA_OPTS -jar $JAR ...'; \
  $REMOTE_ENV nohup java $JAVA_OPTS -jar \"$JAR\" > "\$LOG_FILE" 2>&1 < /dev/null & echo \$! > \"$PID_FILE\"; \
  disown || true; \
  echo 'Backend started. PID: ' \$(cat \"$PID_FILE\") ' Logs: ' "\$LOG_FILE" \
"

echo "Done."
