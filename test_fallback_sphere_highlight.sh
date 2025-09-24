#!/bin/bash

# This script tests the highlighting functionality for fallback spheres
# It creates an object without file and collection, which will use a fallback sphere

echo "Creating a test object without file and collection..."

# Use curl to create a test object via the API
curl -X POST "http://localhost:8080/api/objects" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Test Fallback Sphere",
    "file": "",
    "collection": "",
    "data": "{}",
    "worldId": "test",
    "position": {"x": 0, "y": 0, "z": 0},
    "rotation": {"x": 0, "y": 0, "z": 0},
    "scale": {"x": 1, "y": 1, "z": 1},
    "status": "Fallback Sphere Test"
  }'

echo -e "\n\nTest object created. Please check the browser to verify highlighting works on the fallback sphere."
echo "Instructions:"
echo "1. Navigate to http://localhost:4080/test"
echo "2. Hover over the white sphere that appears"
echo "3. Verify that the sphere shows a yellow highlight"
echo "4. Also verify that highlighting still works on other objects in the scene"
