#!/bin/bash

# Kill processes using ports 8080 and 8090

echo "Checking for processes on port 8080..."
PID_8080=$(lsof -ti :8080)
if [ -n "$PID_8080" ]; then
    echo "Killing process(es) on port 8080: $PID_8080"
    kill -9 $PID_8080
    echo "Port 8080 freed"
else
    echo "No process found on port 8080"
fi

echo ""
echo "Checking for processes on port 8090..."
PID_8090=$(lsof -ti :8090)
if [ -n "$PID_8090" ]; then
    echo "Killing process(es) on port 8090: $PID_8090"
    kill -9 $PID_8090
    echo "Port 8090 freed"
else
    echo "No process found on port 8090"
fi

echo ""
echo "Done!"
