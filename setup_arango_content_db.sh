#!/bin/bash

# setup_arango_content_db.sh
# Script to set up the Saigon.Quest uploads/content ArangoDB database and collection
# This script uses arangosh to create the database, users, and collections

# Configuration variables (matching ArangoConfig.kt)
DB_NAME="saigon_quest_uploads"
ADMIN_USER="root"
ADMIN_PASSWORD=""
APP_USER="saigonquest"
APP_PASSWORD="saigonquest"
HOST="localhost"
PORT="8529"

# Collections to create in uploads/content DB
CONTENT_UPLOADS_COLLECTION="content_uploads"

echo "Setting up ArangoDB content (uploads) database for Saigon.Quest..."

# Create the database and collections using arangosh
arangosh \
  --server.endpoint tcp://$HOST:$PORT \
  --server.username "$ADMIN_USER" \
  --server.password "$ADMIN_PASSWORD" \
  --javascript.execute-string "
    // Check if uploads database exists, if not create it
    if (!db._databases().includes('$DB_NAME')) {
      console.log('Creating uploads database: $DB_NAME');
      db._createDatabase('$DB_NAME');
    } else {
      console.log('Database $DB_NAME already exists');
    }

    // Switch to the uploads database
    db._useDatabase('$DB_NAME');

    // Create collection if it doesn't exist
    if (!db._collection('$CONTENT_UPLOADS_COLLECTION')) {
      console.log('Creating collection: $CONTENT_UPLOADS_COLLECTION');
      db._createDocumentCollection('$CONTENT_UPLOADS_COLLECTION');
    } else {
      console.log('Collection $CONTENT_UPLOADS_COLLECTION already exists');
    }

    // Ensure application user exists and grant permissions
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
      users.grantCollection('$APP_USER', '$DB_NAME', '$CONTENT_UPLOADS_COLLECTION', 'rw');
    } catch (e) {
      console.error('Error creating user or granting permissions:', e);
    }
  "

# Check if the command was successful
if [ $? -eq 0 ]; then
  echo "ArangoDB content (uploads) setup completed successfully!"
  echo "Uploads DB: $DB_NAME"
  echo "Uploads Collection: $CONTENT_UPLOADS_COLLECTION"
  echo "Application user: $APP_USER"
  echo ""
  echo "To update ArangoConfig.kt with the new user credentials:"
  echo "1. Open: /home/jacobferrero/IdeaProjects/Saigon.Quest/backend/src/main/kotlin/database/ArangoConfig.kt"
  echo "2. Update USERNAME to: $APP_USER"
  echo "3. Update PASSWORD to: $APP_PASSWORD"
  echo ""
  echo "Note: In production, use environment variables for credentials instead of hardcoding them."
else
  echo "Error setting up ArangoDB content (uploads) DB. Please check if ArangoDB is running and credentials are correct."
fi
