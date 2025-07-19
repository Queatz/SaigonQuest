# Compose HTML

Welcome!

## Run (development)

`./gradlew jsBrowserDevelopmentRun -t`

## Run

`./gradlew jsBrowserDevelopmentRun`

## Build

`./gradlew jsBrowserProductionWebpack`

Files are in `build/kotlin-webpack/js/productionExecutable`

## Deploy

```shell
apt update
apt install certbot nodejs npm nginx python3-certbot-nginx
```

### HTTP → HTTPS

1. Configure Nginx

2. Replace the contents of `/etc/nginx/sites-enabled/default` with the following

```
server {
    server_name <domain>;
    root /root/ui;
    gzip on;
    gzip_types text/plain application/javascript;

    listen 80;

    location / {
        index index.html;
        try_files $uri $uri/ /index.html =404;
    }
}
```

`chmod 755 /root`

3. Finally

```shell
certbot --nginx
nginx -t
service nginx restart
```
