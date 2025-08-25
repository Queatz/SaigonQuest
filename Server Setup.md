## Initialize a new server

Choose Ubuntu server.

```bash
apt update && apt upgrade
apt install rsync nano
```

## UI

```bash
apt install nano certbot nodejs npm nginx python3-certbot-nginx
```

## Backend & Content

```bash
apt install nano certbot nginx default-jre python3-certbot-nginx
```

```bash
certbot --nginx
nginx -t
service nginx restart 
```

```bash
curl -OL  https://download.arangodb.com/9c169fe900ff79790395784287bfa82f0dc0059375a34a2881b9b745c8efd42e/arangodb312/DEBIAN/Release.key
sudo apt-key add - < Release.key
echo 'deb  https://download.arangodb.com/9c169fe900ff79790395784287bfa82f0dc0059375a34a2881b9b745c8efd42e/arangodb312/DEBIAN/ /' | sudo tee /etc/apt/sources.list.d/arangodb.list
sudo apt-get install apt-transport-https
sudo apt-get update
sudo apt-get install arangodb3e=3.12.5.2-1
```

```bash
apt install openjdk-21-jdk -y
```

`./deploy-backend.sh -b`
`./deploy-content.sh -b`

`./init-db-backend.sh`
`./init-db-content.sh`

`./run-backend.sh`
`./run-content.sh`
