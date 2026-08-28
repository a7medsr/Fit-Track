# Firebase setup

The code is complete, but **sign-in and sync cannot work until you create a
Firebase project**. `app/google-services.json` is currently a placeholder with
fake ids so the project still compiles — every value in it is invalid.

## 1. Create the project

1. <https://console.firebase.google.com> → **Add project**
2. **Add app → Android**
3. Package name must be exactly `com.example.fittrack`

## 2. Register your signing fingerprint (required for Google sign-in)

Email/password works without this. Google sign-in fails with an unhelpful error
if you skip it.

```bash
./gradlew signingReport
```

Copy the **SHA1** from the `debug` variant, then in the Firebase console go to
**Project settings → Your apps → Add fingerprint**. Add your release SHA-1 too
when you ship.

## 3. Enable the sign-in providers

**Build → Authentication → Get started → Sign-in method**, then enable:

- **Email/Password**
- **Google**

Enabling Google is what creates the OAuth web client that
`R.string.default_web_client_id` resolves to. Without it, the Google button
reports that sign-in is not configured.

## 4. Create the database

**Build → Firestore Database → Create database**. Pick a region close to you.

## 5. Publish the security rules

Copy `firestore.rules` from the repo root into **Firestore Database → Rules**
and press **Publish**.

Do not skip this. A database left in test mode is readable and writable by any
authenticated user, which means anyone with an account can read everyone's
workouts.

## 6. Replace the placeholder config

Download `google-services.json` from **Project settings → Your apps** and
overwrite `app/google-services.json`.

Then rebuild:

```bash
./gradlew installDebug
```

## What gets stored

Per signed-in user, under `users/{uid}/data/`:

| Document    | Contents                                                        |
|-------------|-----------------------------------------------------------------|
| `workouts`  | every logged workout                                             |
| `exercises` | your custom exercises, plus favourite / last-used marks          |
| `routines`  | saved gym sessions and their exercise lists                      |
| `steps`     | daily step totals                                                |
| `profile`   | body weight and daily step goal                                  |

The 100 bundled exercises are **not** uploaded — they ship inside the APK and
are identical for every user, so only your own additions and overrides travel.
