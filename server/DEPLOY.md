# Deploying the avatar service

Your box, as reported: **Fedora 42, nginx active on 80/443, Node installed, no
PHP.** So this is a small Node service on localhost with nginx in front, which
also serves the stored images as static files.

You already have a working Let's Encrypt setup (the cert on 443 is valid, for
`containerscanner.tecisfun.cloud`), so certbot is installed and you control the
`tecisfun.cloud` zone. That makes a new subdomain the clean route.

## 1. Point a subdomain at the box

Add an A record for `fittrack.tecisfun.cloud` → your VPS IP.

The existing cert does **not** cover `srv1236384.hstgr.cloud`, so the app cannot
use that hostname: Android rejects a certificate whose name does not match, and
there is no way around that short of shipping a pinned cert.

## 2. Create a service user and directories

Do not run this as root. It accepts uploads from the internet.

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin fittrack
sudo mkdir -p /opt/fittrack /var/lib/fittrack/avatars /etc/fittrack
sudo chown -R fittrack:fittrack /opt/fittrack /var/lib/fittrack
sudo chmod 750 /etc/fittrack
```

`/var/lib/fittrack/avatars` is deliberately **outside** any web root. nginx
serves it explicitly, so nothing else in that tree is ever reachable.

## 3. Firebase service account

The service verifies the app's Firebase ID token, so it needs admin credentials:

Firebase console → Project settings → Service accounts → **Generate new private
key**. Put the JSON at `/etc/fittrack/firebase-service-account.json`, then:

```bash
sudo chown fittrack:fittrack /etc/fittrack/firebase-service-account.json
sudo chmod 600 /etc/fittrack/firebase-service-account.json
```

## 4. Install

```bash
sudo cp server.js package.json /opt/fittrack/
cd /opt/fittrack && sudo -u fittrack npm install --omit=dev
```

## 5. systemd unit

`/etc/systemd/system/fittrack-avatar.service`:

```ini
[Unit]
Description=FitTrack avatar service
After=network.target

[Service]
Type=simple
User=fittrack
Group=fittrack
WorkingDirectory=/opt/fittrack
Environment=PORT=3001
Environment=AVATAR_DIR=/var/lib/fittrack/avatars
Environment=PUBLIC_BASE_URL=https://fittrack.tecisfun.cloud
Environment=GOOGLE_APPLICATION_CREDENTIALS=/etc/fittrack/firebase-service-account.json
ExecStart=/usr/bin/node server.js
Restart=on-failure

# It handles untrusted input, so give it as little of the system as possible.
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/fittrack/avatars

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now fittrack-avatar
sudo systemctl status fittrack-avatar
curl -s localhost:3001/health   # {"ok":true}
```

## 6. nginx vhost

`/etc/nginx/conf.d/fittrack.conf`:

```nginx
server {
    listen 80;
    server_name fittrack.tecisfun.cloud;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    http2 on;
    server_name fittrack.tecisfun.cloud;

    # certbot fills these in (step 7)
    # ssl_certificate     /etc/letsencrypt/live/fittrack.tecisfun.cloud/fullchain.pem;
    # ssl_certificate_key /etc/letsencrypt/live/fittrack.tecisfun.cloud/privkey.pem;

    # Must be >= the service's own 8 MB cap, or nginx rejects first with a
    # 413 that the app cannot explain usefully.
    client_max_body_size 10m;

    location /avatar {
        proxy_pass http://127.0.0.1:3001;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # The stored images, served straight from disk.
    location /avatars/ {
        alias /var/lib/fittrack/avatars/;
        autoindex off;
        add_header Cache-Control "public, max-age=86400";

        # Nothing in here is ever executed or interpreted, whatever it claims
        # to be. This is the line that stops an upload becoming code execution.
        default_type image/jpeg;
        types { image/jpeg jpg jpeg; image/png png; }
    }
}
```

```bash
sudo nginx -t && sudo systemctl reload nginx
```

## 7. Certificate

```bash
sudo certbot --nginx -d fittrack.tecisfun.cloud
```

Confirm Android will accept it — this must print `ssl_verify_result=0`:

```bash
curl -sI -o /dev/null -w "%{http_code} verify=%{ssl_verify_result}\n" https://fittrack.tecisfun.cloud/avatar
```

## 8. Point the app at it

In `local.properties` (gitignored):

```properties
VPS_BASE_URL=https://fittrack.tecisfun.cloud/
```

Trailing slash matters: Retrofit resolves relative paths against it.

## What the endpoint does

`POST /avatar`, `Authorization: Bearer <firebase id token>`, multipart field
`image`. Returns `{"url": "https://…/avatars/<uid>.jpg?v=…"}`.

Security decisions worth knowing, because they are the ones that usually go
wrong on upload endpoints:

- **The filename comes from the verified token**, never from the client. A
  client-supplied name is how `../../` writes outside the folder, and how one
  user overwrites another's picture.
- **The image is decoded and re-encoded** rather than trusting `Content-Type`.
  That rejects anything that is not really an image, and neutralises a file that
  is a valid JPEG *and* a valid script.
- **EXIF is dropped** by the re-encode. Phone photos routinely carry GPS
  coordinates, and these images are served publicly.
- **The folder sits outside the web root** and is served with no interpreter.
- **Size is capped twice**, at nginx and at multer.
- **The service runs unprivileged** under systemd with a read-only view of the
  filesystem apart from the avatar directory.

One thing left open: anyone who knows a uid can fetch that avatar, since the
files are public static URLs. That is normal for profile pictures, but if you
want them private, the fix is to serve them through the Node service behind the
same token check instead of by nginx directly.
