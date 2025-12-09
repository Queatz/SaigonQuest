#!/bin/bash

# setup_arango_db.sh
# Script to set up ArangoDB database and collections for Saigon.Quest
# This script uses arangosh to create the database, users, and collections

# Configuration variables (matching ArangoConfig.kt)
DB_NAME="saigon_quest"
UPLOADS_DB_NAME="saigon_quest_uploads"
ADMIN_USER="root"
ADMIN_PASSWORD="root" # May be ""
APP_USER="saigonquest"
APP_PASSWORD="saigonquest"
HOST="localhost"
PORT="8529"

# Collections to create
WORLDS_COLLECTION="worlds"
USERS_COLLECTION="users"
CONTENT_UPLOADS_COLLECTION="content_uploads"

echo "Setting up ArangoDB for Saigon.Quest..."

# Create the database and collections using arangosh
arangosh \
  --server.endpoint tcp://$HOST:$PORT \
  --server.username $ADMIN_USER \
  --server.password $ADMIN_PASSWORD \
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

    // Create application user if it doesn't exist
    try {
      const users = require('@arangodb/users');
      if (!users.exists('$APP_USER')) {
        console.log('Creating user: $APP_USER');
        users.save('$APP_USER', '$APP_PASSWORD');
        users.grantDatabase('$APP_USER', '$DB_NAME', 'rw');

        // Grant access to collections
        users.grantCollection('$APP_USER', '$DB_NAME', '$WORLDS_COLLECTION', 'rw');
        users.grantCollection('$APP_USER', '$DB_NAME', '$USERS_COLLECTION', 'rw');
      } else {
        console.log('User $APP_USER already exists');
        // Update permissions to ensure they are correct
        users.grantDatabase('$APP_USER', '$DB_NAME', 'rw');
        users.grantCollection('$APP_USER', '$DB_NAME', '$WORLDS_COLLECTION', 'rw');
        users.grantCollection('$APP_USER', '$DB_NAME', '$USERS_COLLECTION', 'rw');
      }
    } catch (e) {
      console.error('Error creating user:', e);
    }

    // --- Setup uploads database ---
    if (!db._databases().includes('$UPLOADS_DB_NAME')) {
      console.log('Creating uploads database: $UPLOADS_DB_NAME');
      db._createDatabase('$UPLOADS_DB_NAME');
    } else {
      console.log('Database $UPLOADS_DB_NAME already exists');
    }

    db._useDatabase('$UPLOADS_DB_NAME');

    if (!db._collection('$CONTENT_UPLOADS_COLLECTION')) {
      console.log('Creating collection: $CONTENT_UPLOADS_COLLECTION');
      db._createDocumentCollection('$CONTENT_UPLOADS_COLLECTION');
    } else {
      console.log('Collection $CONTENT_UPLOADS_COLLECTION already exists');
    }

    try {
      const users = require('@arangodb/users');
      // Ensure the same app user has access to uploads DB and collection
      users.grantDatabase('$APP_USER', '$UPLOADS_DB_NAME', 'rw');
      users.grantCollection('$APP_USER', '$UPLOADS_DB_NAME', '$CONTENT_UPLOADS_COLLECTION', 'rw');
    } catch (e) {
      console.error('Error granting user access to uploads DB:', e);
    }
  "

# Check if the command was successful
if [ $? -eq 0 ]; then
  echo "ArangoDB setup completed successfully!"
  echo "Database: $DB_NAME"
  echo "Collections: $WORLDS_COLLECTION, $USERS_COLLECTION"
  echo "Uploads DB: $UPLOADS_DB_NAME"
  echo "Uploads Collections: $CONTENT_UPLOADS_COLLECTION"
  echo "Application user: $APP_USER"
  
  echo ""
  echo "To update ArangoConfig.kt with the new user credentials:"
  echo "1. Open: /home/jacobferrero/IdeaProjects/Saigon.Quest/backend/src/main/kotlin/database/ArangoConfig.kt"
  echo "2. Update USERNAME to: $APP_USER"
  echo "3. Update PASSWORD to: $APP_PASSWORD"
  echo ""
  echo "Note: In production, use environment variables for credentials instead of hardcoding them."
else
  echo "Error setting up ArangoDB. Please check if ArangoDB is running and credentials are correct."
fi
