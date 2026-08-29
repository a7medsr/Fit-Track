# Deploying the FitTrack media service

Fedora 42, nginx on 80/443, Node, no PHP. A small Node service on localhost with
nginx in front, which also serves the stored images as static files.

It handles two things: **avatars** (one per user, overwritten in place) and
**community post images** (many per user, deletable).

---

## Already running the avatar-only version? Start here

Three things changed: a second image directory, two new endpoints, and TLS.

```bash
# 1. New directory for post images
sudo install -d -o fittrack -g fittrack -m 755 /var/lib/fittrack/posts

# 2. New code
sudo cp server.js /opt/fittrack/server.js
sudo chown fittrack:fittrack /opt/fittrack/server.js

# 3. Tell systemd where post images live and let it write there
sudo systemctl edit --full fittrack-avatar     # add the two lines marked NEW below
sudo systemctl daemon-reload
sudo systemctl restart fittrack-avatar
curl -s localhost:3001/health                  # {"ok":true}
```

Then do the nginx and TLS steps (5 and 6). Skip everything else.

---

## 1. Point a subdomain at the box

Add a DNS **A record**: `fittrack.tecisfun.cloud` → `147.93.55.224`.

This is not optional now that TLS is in play. The app cannot use
`srv1236384.hstgr.cloud` over https — your existing certificate covers
`containerscanner.tecisfun.cloud` only, and Android rejects a certificate whose
name does not match. No certificate authority will issue one for a bare IP
either.

Confirm it has propagated before running certbot, or certbot will fail:

```bash
dig +short fittrack.tecisfun.cloud
```

## 2. Service user and directories

Do not run this as root. It accepts uploads from the internet.

```bash
sudo useradd --system --shell /usr/sbin/nologin fittrack
sudo install -d -o fittrack -g fittrack /home/fittrack
sudo mkdir -p /opt/fittrack /var/lib/fittrack/avatars /var/lib/fittrack/posts /etc/fittrack
sudo chown -R fittrack:fittrack /opt/fittrack /var/lib/fittrack
sudo chmod 750 /etc/fittrack
```

`/var/lib/fittrack` is deliberately **outside** any web root. nginx serves the
two image directories explicitly, so nothing else in that tree is reachable.

## 3. Firebase service account

The service verifies the app's Firebase ID token, and checks community admin
rights when an admin deletes someone else's photo, so it needs admin
credentials.

Firebase console → Project settings → Service accounts → **Generate new private
key**. Put the JSON at `/etc/fittrack/firebase-service-account.json`, then:

```bash
sudo chown fittrack:fittrack /etc/fittrack/firebase-service-account.json
sudo chmod 600 /etc/fittrack/firebase-service-account.json
```

That file is effectively a root password for your entire Firebase project.
Delete the copy in your Downloads folder once it is transferred.

## 4. Install and run

```bash
sudo cp server.js package.json /opt/fittrack/
sudo chown fittrack:fittrack /opt/fittrack/server.js /opt/fittrack/package.json
cd /opt/fittrack && sudo -u fittrack -H npm install --omit=dev
```

`/etc/systemd/system/fittrack-avatar.service`:

```ini
[Unit]
Description=FitTrack media service
After=network.target

[Service]
Type=simple
User=fittrack
Group=fittrack
WorkingDirectory=/opt/fittrack
Environment=PORT=3001
Environment=AVATAR_DIR=/var/lib/fittrack/avatars
Environment=POST_DIR=/var/lib/fittrack/posts
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
ReadWritePaths=/var/lib/fittrack/posts

[Install]
WantedBy=multi-user.target
```

The two lines marked NEW for an existing install are `POST_DIR` and the second
`ReadWritePaths`. Without the latter, `ProtectSystem=strict` makes every post
upload fail with EROFS — and the failure looks like a permissions bug, not a
systemd one.

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now fittrack-avatar
curl -s localhost:3001/health   # {"ok":true}
```

## 5. nginx

Two files, because the routes are shared between the TLS block and the
debug-only HTTP one and must not drift apart:

```bash
sudo cp nginx-fittrack-routes.conf /etc/nginx/snippets/fittrack-routes.conf
sudo cp nginx-fittrack.conf /etc/nginx/conf.d/fittrack.conf
sudo rm -f /etc/nginx/conf.d/fittrack-http.conf   # if the old one is there
sudo nginx -t && sudo systemctl reload nginx
```

## 6. Certificate

```bash
sudo certbot --nginx -d fittrack.tecisfun.cloud
```

certbot rewrites the first server block into a TLS one. Confirm Android will
accept the result — this must print `verify=0`:

```bash
curl -sI -o /dev/null -w "%{http_code} verify=%{ssl_verify_result}\n" \
  https://fittrack.tecisfun.cloud/health
```

`verify=0` means the chain is trusted. Anything else and the app will fail to
connect with an opaque TLS error, so do not move on until this is clean.

## 7. Point the app at it

In `local.properties` (gitignored):

```properties
VPS_BASE_URL=https://fittrack.tecisfun.cloud/
```

Trailing slash matters: Retrofit resolves relative paths against it.

---

## The endpoints

| | |
|---|---|
| `GET /health` | `{"ok":true}` |
| `POST /avatar` | multipart `image`, returns `{"url":"…/avatars/<uid>.jpg?v=…"}` |
| `POST /post-image` | multipart `image`, returns `{"imageId":"…","url":"…/post-images/<id>.jpg"}` |
| `DELETE /post-image/:imageId` | optional `?communityId=` for admin deletion |

All except `/health` require `Authorization: Bearer <firebase id token>`.

## Security decisions worth knowing

These are the ones that usually go wrong on upload endpoints.

- **The uid comes from the verified token**, never from the client. A
  client-supplied name is how `../../` writes outside the folder, and how one
  user overwrites another's picture.
- **Every image is decoded and re-encoded** rather than trusted by
  `Content-Type`. That rejects anything that is not really an image, and
  neutralises a file that is a valid JPEG *and* a valid script.
- **EXIF is dropped** by the re-encode. Phone photos routinely carry GPS
  coordinates, and these images are served publicly.
- **Post images carry a random suffix**, so one post's photo cannot be guessed
  from another's, and are named with the uploader's uid as a prefix, which is
  what makes a delete request authorisable.
- **Admin deletion is checked against Firestore**, not against what the client
  claims. And the check is narrow: an admin may only delete a photo belonging to
  someone who is actually a member of the community they administer. Without
  that second half, any admin anywhere could delete any photo by naming their
  own group.
- **Deletion is idempotent** — already-gone returns success. The app deletes an
  orphaned upload after a failed post write, and that retry must not fail.
- **Two storage ceilings**, per user and overall, because uploads are unbounded
  and the disk is not.
- **Size is capped twice**, at nginx and at multer.
- **The service runs unprivileged** under systemd with a read-only view of the
  filesystem apart from the two image directories.

Anyone who knows a uid can fetch that avatar, and anyone with a post image URL
can fetch it, since both are public static URLs. That is normal, but it does
mean a community post photo is not private to the community — someone who has
left could still open a link they saved. If that matters, the fix is to serve
them through the Node service behind the same token check instead of by nginx
directly.
