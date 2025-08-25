#!/bin/bash

# setup_arango_backend_db.sh
# Script to set up the main Saigon.Quest ArangoDB database and collections
# This script uses arangosh to create the database, users, and collections

# Configuration variables (matching ArangoConfig.kt)
DB_NAME="saigon_quest"
ADMIN_USER="root"
ADMIN_PASSWORD=""
APP_USER="saigonquest"
APP_PASSWORD="saigonquest"
HOST="localhost"
PORT="8529"

# Collections to create in backend DB
WORLDS_COLLECTION="worlds"
USERS_COLLECTION="users"

echo "Setting up ArangoDB backend database for Saigon.Quest..."

# Create the database and collections using arangosh
arangosh \
  --server.endpoint tcp://$HOST:$PORT \
  --server.username "$ADMIN_USER" \
  --server.password "$ADMIN_PASSWORD" \
  --javascript.execute-string "
    // Check if database exists, if not create it
    if (!db._databases().includes('$DB_NAME')) {
      console.log('Creating database: $DB_NAME');
      db._createDatabase('$DB_NAME');
    } else {
      console.log('Database $DB_NAME already exists');
    }

    // Switch to the database
    db._useDatabase('$DB_NAME');

    // Create collections if they don't exist
    if (!db._collection('$WORLDS_COLLECTION')) {
      console.log('Creating collection: $WORLDS_COLLECTION');
      db._createDocumentCollection('$WORLDS_COLLECTION');
    } else {
      console.log('Collection $WORLDS_COLLECTION already exists');
    }

    if (!db._collection('$USERS_COLLECTION')) {
      console.log('Creating collection: $USERS_COLLECTION');
      db._createDocumentCollection('$USERS_COLLECTION');
    } else {
      console.log('Collection $USERS_COLLECTION already exists');
    }

    // Create application user if it doesn't exist and grant permissions
    try {
      const users = require('@arangodb/users');
      if (!users.exists('$APP_USER')) {
        console.log('Creating user: $APP_USER');
        users.save('$APP_USER', '$APP_PASSWORD');
      } else {
        console.log('User $APP_USER already exists');
      }
      // Ensure permissions are correct
      users.grantDatabase('$APP_USER', '$DB_NAME', 'rw');
      users.grantCollection('$APP_USER', '$DB_NAME', '$WORLDS_COLLECTION', 'rw');
      users.grantCollection('$APP_USER', '$DB_NAME', '$USERS_COLLECTION', 'rw');
    } catch (e) {
      console.error('Error creating user or granting permissions:', e);
    }
  "

# Check if the command was successful
if [ $? -eq 0 ]; then
  echo "ArangoDB backend setup completed successfully!"
  echo "Database: $DB_NAME"
  echo "Collections: $WORLDS_COLLECTION, $USERS_COLLECTION"
  echo "Application user: $APP_USER"
  echo ""
  echo "To update ArangoConfig.kt with the new user credentials:"
  echo "1. Open: /home/jacobferrero/IdeaProjects/Saigon.Quest/backend/src/main/kotlin/database/ArangoConfig.kt"
  echo "2. Update USERNAME to: $APP_USER"
  echo "3. Update PASSWORD to: $APP_PASSWORD"
  echo ""
  echo "Note: In production, use environment variables for credentials instead of hardcoding them."
else
  echo "Error setting up ArangoDB backend DB. Please check if ArangoDB is running and credentials are correct."
fi
