'use strict';

/**
 * FitTrack avatar service.
 *
 * Accepts one image per signed-in user, re-encodes it, and writes it to a
 * folder on disk. The URL it returns is what the app stores in Firestore; the
 * image bytes never go near the database.
 *
 * Runs behind nginx on localhost. It must not be exposed directly, and must not
 * run as root -- see fittrack-avatar.service.
 */

const express = require('express');
const multer = require('multer');
const sharp = require('sharp');
const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');

const PORT = parseInt(process.env.PORT || '3001', 10);
const AVATAR_DIR = process.env.AVATAR_DIR || '/var/lib/fittrack/avatars';
const PUBLIC_BASE_URL = (process.env.PUBLIC_BASE_URL || '').replace(/\/+$/, '');
const MAX_UPLOAD_BYTES = 8 * 1024 * 1024; // generous for a phone photo, bounded for disk
const OUTPUT_PX = 512;

if (!PUBLIC_BASE_URL) {
  console.error('PUBLIC_BASE_URL is required so generated URLs are absolute.');
  process.exit(1);
}

fs.mkdirSync(AVATAR_DIR, { recursive: true });

admin.initializeApp({ credential: admin.credential.applicationDefault() });

const app = express();
app.disable('x-powered-by');

// Memory storage on purpose: nothing untrusted is written to disk until it has
// been decoded and re-encoded below.
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: MAX_UPLOAD_BYTES, files: 1 }
});

/**
 * Every request must carry a Firebase ID token. The uid comes from the verified
 * token and nothing else -- never from a form field or a filename, or one user
 * could overwrite another's picture by editing the request.
 */
async function requireUser(req, res, next) {
  const header = req.get('authorization') || '';
  const match = header.match(/^Bearer\s+(.+)$/i);
  if (!match) return res.status(401).json({ error: 'Missing bearer token' });

  try {
    req.user = await admin.auth().verifyIdToken(match[1]);
    next();
  } catch (err) {
    res.status(401).json({ error: 'Invalid or expired token' });
  }
}

/** A uid becomes part of a filename, so anything path-like is refused. */
function safeUid(uid) {
  return /^[A-Za-z0-9_-]{1,128}$/.test(uid) ? uid : null;
}

app.get('/health', (req, res) => res.json({ ok: true }));

app.post('/avatar', requireUser, upload.single('image'), async (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'No image supplied' });

  const uid = safeUid(req.user.uid);
  if (!uid) return res.status(400).json({ error: 'Unusable account id' });

  try {
    // sharp only succeeds on a real image, so this both validates the content
    // and strips everything that is not pixels. A file that is a valid JPEG and
    // also a valid script is the classic upload attack; re-encoding kills it,
    // and it drops EXIF, including the GPS coordinates phone photos carry.
    const jpeg = await sharp(req.file.buffer, { failOn: 'error' })
      .rotate()
      .resize(OUTPUT_PX, OUTPUT_PX, { fit: 'cover', position: 'centre' })
      .jpeg({ quality: 85, mozjpeg: true })
      .toBuffer();

    const filename = `${uid}.jpg`;
    const target = path.join(AVATAR_DIR, filename);

    // Belt and braces: the resolved path must still sit inside AVATAR_DIR.
    if (path.dirname(path.resolve(target)) !== path.resolve(AVATAR_DIR)) {
      return res.status(400).json({ error: 'Bad path' });
    }

    // Write then rename, so a reader never sees a half-written file.
    const temp = `${target}.tmp`;
    await fs.promises.writeFile(temp, jpeg, { mode: 0o644 });
    await fs.promises.rename(temp, target);

    // Cache-busting suffix: the filename is stable per user, so without it the
    // app and any CDN would keep showing the previous picture.
    const url = `${PUBLIC_BASE_URL}/avatars/${filename}?v=${Date.now()}`;
    res.json({ url });
  } catch (err) {
    if (err && err.message && /unsupported image|Input buffer/i.test(err.message)) {
      return res.status(400).json({ error: 'That file is not a readable image' });
    }
    console.error('avatar upload failed', err);
    res.status(500).json({ error: 'Could not store the image' });
  }
});

// multer's own errors arrive here; report the size cap properly rather than 500.
app.use((err, req, res, next) => {
  if (err instanceof multer.MulterError) {
    const tooBig = err.code === 'LIMIT_FILE_SIZE';
    return res.status(tooBig ? 413 : 400).json({
      error: tooBig ? 'Image is too large' : 'Bad upload'
    });
  }
  next(err);
});

app.listen(PORT, '127.0.0.1', () => {
  console.log(`fittrack avatar service on 127.0.0.1:${PORT}, files in ${AVATAR_DIR}`);
});
