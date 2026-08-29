# Community feature — what you have to do before it works

The app code is done and builds. Three things live outside the repository and
none of them can be done from here.

Until all three are in place the Community tab opens but every action fails with
a permission error, which is the security rules doing their job.

---

## 1. Publish the Firestore rules

Firebase console → Firestore Database → Rules → paste [firestore.rules](firestore.rules) → **Publish**.

This is not optional and it is not a formality. The rules are the only thing
standing between the community tree and any signed-in user: without them,
anyone could rename your community, approve themselves into it, or delete
someone else's posts. The client never checks permission for real — it only
decides which buttons to draw.

What they enforce, in short:

- A community's roster, posts, comments and scores are readable **only by
  members**. The public directory sees the name, icon and member count, nothing
  else.
- Membership is granted **only by the admin**. A joiner can write a request and
  nothing more, which is what makes approval mean anything.
- A removed member is on `bannedUids`, and the rule that creates a join request
  refuses anyone on that list — so removal cannot be undone by the person
  removed.
- A member may edit the community document in exactly one way: to take
  themselves out of it. The admin is excluded from that, so a community is never
  left without one.
- `memberCount` must always equal `memberUids.size()`. Otherwise the directory's
  sort order becomes something anyone can fake.
- A post's author is taken from the token, never from the request body.

## 2. Create the one composite index

The feed needs a single collection-group query — "every reaction this user left
in this community" — which Firestore cannot serve without an index.

Easiest route: open the Community tab, look at a feed, and Firestore prints a
ready-made creation link in logcat. Or create it by hand:

Firebase console → Firestore → Indexes → Composite → **Add index**

| | |
|---|---|
| Collection ID | `reactions` |
| Query scope | **Collection group** |
| Field 1 | `uid` — Ascending |
| Field 2 | `communityId` — Ascending |

Definition is in [firestore.indexes.json](firestore.indexes.json).

Without it the feed still loads; reactions just show as un-chosen, because the
lookup fails and is caught. With it, one query replaces one read per post.

## 3. Deploy the server and turn on HTTPS

Post photos need the new endpoints, and you asked for TLS first. Full steps are
in [server/DEPLOY.md](server/DEPLOY.md) — start at *"Already running the
avatar-only version?"*.

The short version:

```bash
sudo install -d -o fittrack -g fittrack -m 755 /var/lib/fittrack/posts
sudo cp server.js /opt/fittrack/server.js
sudo chown fittrack:fittrack /opt/fittrack/server.js
```

Add to `/etc/systemd/system/fittrack-avatar.service`:

```ini
Environment=POST_DIR=/var/lib/fittrack/posts
Environment=PUBLIC_BASE_URL=https://fittrack.tecisfun.cloud
ReadWritePaths=/var/lib/fittrack/posts
```

That `ReadWritePaths` line matters more than it looks: `ProtectSystem=strict` is
already on, so without it every photo upload fails with a read-only filesystem
error that looks like a permissions bug rather than a systemd one.

```bash
sudo systemctl daemon-reload && sudo systemctl restart fittrack-avatar
curl -s localhost:3001/health     # {"ok":true}
```

Then DNS, nginx and the certificate:

```bash
dig +short fittrack.tecisfun.cloud     # must return 147.93.55.224 first
sudo cp server/nginx-fittrack-routes.conf /etc/nginx/snippets/fittrack-routes.conf
sudo cp server/nginx-fittrack.conf /etc/nginx/conf.d/fittrack.conf
sudo rm -f /etc/nginx/conf.d/fittrack-http.conf
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d fittrack.tecisfun.cloud
```

Confirm Android will accept the certificate — this must print `verify=0`:

```bash
curl -sI -o /dev/null -w "%{http_code} verify=%{ssl_verify_result}\n" https://fittrack.tecisfun.cloud/health
```

Finally, in `local.properties` (gitignored):

```properties
VPS_BASE_URL=https://fittrack.tecisfun.cloud/
```

The existing certificate covers `containerscanner.tecisfun.cloud` only. Android
rejects a certificate whose name does not match, and no authority will issue one
for a bare IP, so `srv1236384.hstgr.cloud` and `147.93.55.224` cannot be used
over HTTPS.

---

## How it is put together

### Two different shapes in one database

The rest of the app mirrors each user's private data as **whole-list replaces** —
one document per collection holding an array, pushed wholesale. That is safe
only because exactly one device owns it.

A community is the opposite: many people writing at once. A whole-list replace
would silently drop whatever landed in between, so nothing here is written that
way. Every shared value is either its own document or an atomic operation, and
`memberCount` is computed inside a transaction from the array it was read with
rather than nudged with `increment`, so it cannot drift.

```
communities/{code}                     name, icon, adminUid, metric,
                                       memberUids[], pendingUids[], bannedUids[]
  members/{uid}                        name, photo, joinedAt
  requests/{uid}                       waiting for the admin
  scores/{weekId}_{uid}                one member's weekly number
  posts/{postId}                       text, photo URL, tallies
    reactions/{uid}                    one per person per post
    comments/{commentId}               flat, no replies
```

The three uid arrays live on the parent document on purpose: a security rule can
check them without a second read, and the directory can show "Requested" on
twenty communities without twenty extra queries.

### The leaderboard is self-reported, and says so

Nobody can read anybody else's steps — the rules forbid it, correctly. So each
member's own device computes their weekly number and publishes it when they open
a community screen.

Two consequences, both deliberate and both visible in the UI:

- **A score is only as fresh as the last time its owner opened the app.** Every
  row shows "updated 3d ago" for exactly this reason. Without it, a member who
  has been busy looks like they stopped moving.
- **The number is client-reported**, so a modified app could send anything. You
  chose to accept this, which is the right call for a friends' leaderboard. The
  authorship is *not* forgeable though: the rules pin each score document to the
  uid in the token, so nobody can write a score onto someone else's name.

The week is Monday–Sunday in each device's local time, so two members in
different time zones can briefly disagree about when it turned over.

### Photos

Post images go to `/var/lib/fittrack/posts` on the VPS under a random filename
prefixed with the uploader's uid; only the URL reaches Firestore. The uid prefix
is what lets a delete be authorised — and an admin deleting someone else's photo
is checked **against Firestore by the server**, never against what the client
claims. That check is deliberately narrow: an admin may only delete a photo
belonging to somebody who is actually a member of the community they run.

Two ceilings, because uploads are unbounded and your disk is not: 60 images per
person, 20 000 overall.

---

## Known limits

- **No push notifications.** You see reactions and comments when you open the
  app. Adding them means FCM plus a sender on the VPS.
- **No live updates.** The feed refreshes when you open or return to the screen,
  not while you watch it. Live listeners are what turn a free Firestore tier into
  a bill.
- **Community data is not in Room**, so with no signal you get Firestore's own
  cache and posting is blocked. The rest of the app still works offline exactly
  as before.
- **Post photos are public URLs.** Someone who saved a link keeps access to that
  image after leaving the community. Normal for this kind of feature, but worth
  knowing. Fixing it means serving images through Node behind the token check
  instead of straight off nginx.
- **Deleting a large community** clears up to 300 posts from the phone. Beyond
  that some documents would be left orphaned.
- **No reporting or blocking.** You said course/portfolio, so these are out. They
  become mandatory if this ever goes to the Play Store.
