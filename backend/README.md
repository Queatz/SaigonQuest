# Saigon.Quest Backend

This directory contains the backend code for the Saigon.Quest project.

## Database Setup

The project uses ArangoDB as its database. A setup script is provided to create the necessary database, collections, and users.

### Prerequisites

- ArangoDB installed and running on your system
- `arangosh` command available in your PATH

### Setting Up ArangoDB

1. Make sure ArangoDB is running
2. Run the setup script:

```bash
./setup_arango_db.sh
```

The script will:
- Create a database named `saigon_quest` (if it doesn't exist)
- Create two collections: `worlds` and `users` (if they don't exist)
- Create an application user with appropriate permissions
- Provide instructions for updating the application configuration

### Testing the Setup

After running the setup script, you can verify that everything was set up correctly:

1. Connect to ArangoDB using arangosh:

```bash
arangosh --server.endpoint tcp://localhost:8529 --server.username saigon_quest_user --server.password saigon_quest_password --server.database saigon_quest
```

2. List the collections to verify they exist:

```javascript
db._collections();
```

You should see the `worlds` and `users` collections in the output.

3. Try creating a test document:

```javascript
db.users.save({ name: "Test User", email: "test@example.com" });
```

If this succeeds, your database setup is working correctly.

### Updating Application Configuration

After running the setup script, you may need to update the application configuration to use the new database user. The script will provide instructions for this, but generally you'll need to:

1. Open `src/main/kotlin/database/ArangoConfig.kt`
2. Update the `USERNAME` and `PASSWORD` constants to match the ones created by the script

In a production environment, you should use environment variables for these credentials instead of hardcoding them.

## Running the Backend

File: `/etc/nginx/sites-enabled/default`

```conf
server {
    server_name api.saigon.quest; # managed by Certbot

    client_max_body_size 100m;

    keepalive_timeout 300s;
    send_timeout 300s;
    client_body_timeout 300s;
    client_header_timeout 300s;
    proxy_connect_timeout 60s;
    proxy_read_timeout 300s;
    proxy_send_timeout 300s;

        location / {
            proxy_pass http://localhost:8080;
            chunked_transfer_encoding off;
            proxy_buffering off;
            proxy_cache off;
            
            # WebSocket support
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection $http_connection;
        }
}

server {
    if ($host = api.saigon.quest) {
        return 301 https://$host$request_uri;
    } # managed by Certbot


        listen 80 ;
        listen [::]:80 ;
    server_name api.saigon.quest;
    return 404; # managed by Certbot
}

```
