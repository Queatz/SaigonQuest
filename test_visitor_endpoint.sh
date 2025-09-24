#!/bin/bash

# Test the POST /user/visitor endpoint to create a new visitor
# This will test if our fix for the "illegal document key" error works

echo "Testing POST /user/visitor endpoint..."
curl -X POST \
  http://localhost:8080/user/visitor \
  -H "Content-Type: application/json" \
  -d '{}' \
  -v

echo ""
echo "If you don't see an error about 'illegal document key', the fix worked!"
