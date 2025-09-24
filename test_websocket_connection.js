// WebSocket Connection Test Script
// This script tests the WebSocket connection and chat functionality

console.log("Starting WebSocket connection test...");

// Function to test the WebSocket connection
function testWebSocketConnection() {
    console.log("Testing WebSocket connection...");
    
    // Get the current protocol (http or https)
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const host = window.location.host;
    const worldId = "test-world-" + Date.now(); // Use a unique world ID for testing
    
    console.log(`Creating WebSocket connection to ${protocol}//${host}/ws?worldId=${worldId}`);
    
    // Create a WebSocket connection
    const socket = new WebSocket(`${protocol}//${host}/ws?worldId=${worldId}`);
    
    // Set up event handlers
    socket.onopen = function(event) {
        console.log("WebSocket connection opened successfully");
        
        // Send a test message
        sendTestMessage(socket, worldId);
    };
    
    socket.onmessage = function(event) {
        console.log("Received WebSocket message:", event.data);
        
        try {
            const message = JSON.parse(event.data);
            console.log("Parsed message:", message);
            
            if (message.action === "RECEIVE_CHAT_MESSAGE") {
                console.log("Received chat message:", message.data);
                console.log("TEST PASSED: Chat message received successfully");
            }
        } catch (e) {
            console.error("Error parsing WebSocket message:", e);
        }
    };
    
    socket.onclose = function(event) {
        console.log("WebSocket connection closed", event);
    };
    
    socket.onerror = function(event) {
        console.error("WebSocket error:", event);
        console.log("TEST FAILED: WebSocket connection error");
    };
    
    // Return the socket for cleanup
    return socket;
}

// Function to send a test chat message
function sendTestMessage(socket, worldId) {
    console.log("Sending test chat message...");
    
    // Create a test chat message
    const chatMessage = {
        worldId: worldId,
        userId: "test-user-" + Date.now(),
        username: "Test User",
        message: "Test message at " + new Date().toISOString(),
        timestamp: Date.now()
    };
    
    // Create a WebSocket message
    const message = {
        action: "SEND_CHAT_MESSAGE",
        data: JSON.stringify(chatMessage)
    };
    
    // Send the message
    const messageString = JSON.stringify(message);
    console.log("Sending message:", messageString);
    socket.send(messageString);
    
    console.log("Test message sent");
}

// Run the test
const socket = testWebSocketConnection();

// Clean up after 10 seconds
setTimeout(function() {
    console.log("Cleaning up...");
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.close();
    }
    console.log("Test completed");
}, 10000);
