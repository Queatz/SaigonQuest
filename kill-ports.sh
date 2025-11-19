#!/bin/bash

# Kill processes using ports 8080 and 8090

# Function to kill process on a specific port
kill_port() {
    local port=$1
    echo "Checking for processes on port $port..."
    local PID=$(lsof -ti :$port)
    if [ -n "$PID" ]; then
        echo "Killing process(es) on port $port: $PID"
        kill -9 $PID
        echo "Port $port freed"
    else
        echo "No process found on port $port"
    fi
    echo ""
}

# Check if port argument is provided
if [ $# -eq 1 ]; then
    kill_port $1
    echo "Done!"
    exit 0
fi


kill_port 8080
kill_port 8090
kill_port 4080
echo "Done!"
