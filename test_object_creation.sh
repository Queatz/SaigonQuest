#!/bin/bash

# Test script to verify that the "World not found" error is properly handled
# when creating an object with an invalid worldId

# Set API base URL
API_BASE_URL="http://localhost:8080/api"

# Get a valid JWT token (you'll need to replace this with a valid token)
# You can get a token by logging in through the frontend or using the login API
TOKEN="your_jwt_token_here"

# Create an object with an invalid worldId
echo "Testing object creation with invalid worldId..."
curl -X POST \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "title": "Test Object",
    "categories": ["test"],
    "data": "{}",
    "worldId": "invalid_world_id",
    "position": {"x": 0, "y": 0, "z": 0},
    "rotation": {"x": 0, "y": 0, "z": 0},
    "scale": {"x": 1, "y": 1, "z": 1}
  }' \
  $API_BASE_URL/object

echo -e "\n\nExpected response: An error message indicating 'World not found'"
