#!/bin/bash

# ArangoDB Database Reset Script
# This script removes all non-system collections from a specified database

# Configuration
ARANGOSH_PATH="arangosh"  # Update if arangosh is not in PATH
DATABASE_NAME="saigon_quest"  # Change to your database name
USERNAME="saigonquest"           # Default username
PASSWORD="saigonquest"               # Leave empty for no password, or set your password
SERVER_ENDPOINT="tcp://127.0.0.1:8529"  # Update if different

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${2}${1}${NC}"
}

# Check if arangosh is available
if ! command -v $ARANGOSH_PATH &> /dev/null; then
    print_status "Error: arangosh not found. Please install ArangoDB or update ARANGOSH_PATH in the script." "$RED"
    exit 1
fi

# Confirm action
print_status "WARNING: This will remove ALL collections from database: $DATABASE_NAME" "$YELLOW"
print_status "System collections (starting with _) will be preserved." "$YELLOW"
read -p "Are you sure you want to continue? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    print_status "Operation cancelled." "$YELLOW"
    exit 0
fi

# Create temporary JavaScript file
TEMP_JS=$(mktemp)
cat > $TEMP_JS << 'EOF'
// JavaScript to execute in arangosh
try {
    var startTime = new Date();
    print("Connected to database: " + db._name());
    print("Starting database reset...");

    // Get all collections
    var collections = db._collections();
    var userCollections = collections.filter(col => col.name()[0] !== '_');
    var systemCollections = collections.filter(col => col.name()[0] === '_');

    print("Found " + userCollections.length + " user collection(s) to remove");
    print("Found " + systemCollections.length + " system collection(s) that will be preserved");

    if (userCollections.length === 0) {
        print("No user collections found to remove.");
        quit();
    }

    // Remove each user collection
    var removedCount = 0;
    userCollections.forEach(function(col) {
        try {
            print("Dropping collection: " + col.name());
            db._drop(col.name());
            removedCount++;
        } catch (err) {
            print("Error dropping collection " + col.name() + ": " + err.message);
        }
    });

    var endTime = new Date();
    var duration = (endTime - startTime) / 1000;

    print("==========================================");
    print("Reset completed!");
    print("Removed " + removedCount + " collection(s)");
    print("Duration: " + duration + " seconds");
    print("Database: " + db._name() + " is now empty");
    print("==========================================");

} catch (err) {
    print("Error: " + err.message);
    quit(1);
}
EOF

print_status "Executing reset operation..." "$YELLOW"

# Build arangosh command
ARANGOSH_CMD="$ARANGOSH_PATH --server.endpoint $SERVER_ENDPOINT --server.database $DATABASE_NAME"

# Add authentication if password is set
if [ ! -z "$PASSWORD" ]; then
    ARANGOSH_CMD="$ARANGOSH_CMD --server.username $USERNAME --server.password $PASSWORD"
fi

# Execute the JavaScript
$ARANGOSH_CMD --javascript.execute $TEMP_JS

# Clean up
rm $TEMP_JS

print_status "Reset script completed." "$GREEN"
