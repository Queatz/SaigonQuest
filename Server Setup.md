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
apt install nano certbot nodejs npm nginx default-jre python3-certbot-nginx
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
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash
nvm install 24
nvm use 24
```

```bash
apt install openjdk-21-jdk -y
```

```bash
npx web-push generate-vapid-keys
 
=======================================

Public Key:
BMDAzYH2jDNtRAU3lpl9wrU3FDavNV9M5Mu8_GIqkpAWjCYoESAlJb1e2T7Y2NGbdMr4QZBB5Ti4zXzrvPt6aPg

Private Key:
KjuxzDj7xwj0F2tHibLNsItpY-633toE45czbhj7gjs

=======================================
```

Update `run-backend.sh` to use these generated keys:

```
REMOTE_ENV="${REMOTE_ENV:-"VAPID_PUBLIC_KEY=BMDAzYH2jDNtRAU3lpl9wrU3FDavNV9M5Mu8_GIqkpAWjCYoESAlJb1e2T7Y2NGbdMr4QZBB5Ti4zXzrvPt6aPg VAPID_PRIVATE_KEY=KjuxzDj7xwj0F2tHibLNsItpY-633toE45czbhj7gjs"}"
```

`./deploy-backend.sh -b`
`./deploy-content.sh -b`

`./init-db-backend.sh`
`./init-db-content.sh`

`./run-backend.sh`
`./run-content.sh`
