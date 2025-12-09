#!/bin/bash

# Test script for inventory functionality
echo "Testing inventory functionality..."

# Start the backend server if it's not already running
if ! nc -z localhost 8080; then
  echo "Starting backend server..."
  cd backend
  ./gradlew run &
  BACKEND_PID=$!
  cd ..
  
  # Wait for backend to start
  echo "Waiting for backend to start..."
  until nc -z localhost 8080; do
    sleep 1
  done
  echo "Backend started."
fi

# Start the frontend if it's not already running
if ! nc -z localhost 4080; then
  echo "Starting frontend..."
  ./gradlew jsBrowserDevelopmentRun &
  FRONTEND_PID=$!
  
  # Wait for frontend to start
  echo "Waiting for frontend to start..."
  until nc -z localhost 4080; do
    sleep 1
  done
  echo "Frontend started."
fi

echo "Opening browser to test inventory functionality..."
# Open the browser to the application
if command -v xdg-open > /dev/null; then
  xdg-open http://localhost:4080
elif command -v open > /dev/null; then
  open http://localhost:4080
else
  echo "Please open http://localhost:4080 in your browser to test the inventory functionality."
fi

echo "Test steps:"
echo "1. Log in to the application"
echo "2. Check that the inventory tray shows at the bottom of the screen"
echo "3. Click on an item in the tray to use it"
echo "4. Click the + button to open the inventory dialog"
echo "5. Test wearing items by clicking the 'Wear' button or dragging to the character"
echo "6. Test removing worn items by clicking on them in the 'Worn Items' section"
echo "7. Test the refresh button to update the inventory"

echo "Press Ctrl+C to stop the test servers when done."

# Wait for user to finish testing
wait $FRONTEND_PID $BACKEND_PID
