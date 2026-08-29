'use strict';

/**
 * FitTrack media service.
 *
 * Two kinds of image live here:
 *
 *   /avatar      one per user, fixed filename, overwritten in place.
 *   /post-image  many per user, random filename, deletable.
 *
 * In both cases the URL is what the app stores in Firestore; the image bytes
 * never go near the database.
 *
 * Runs behind nginx on localhost. It must not be exposed directly, and must not
 * run as root -- see fittrack-avatar.service.
 */

const express = require('express');
const multer = require('multer');
const sharp = require('sharp');
const admin = require('firebase-admin');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const PORT = parseInt(process.env.PORT || '3001', 10);
const AVATAR_DIR = process.env.AVATAR_DIR || '/var/lib/fittrack/avatars';
const POST_DIR = process.env.POST_DIR || '/var/lib/fittrack/posts';
const PUBLIC_BASE_URL = (process.env.PUBLIC_BASE_URL || '').replace(/\/+$/, '');
const MAX_UPLOAD_BYTES = 8 * 1024 * 1024; // generous for a phone photo, bounded for disk
const AVATAR_PX = 512;
const POST_MAX_PX = 1080;

// Uploads are unbounded by nature, and the disk is not. Two ceilings: one per
// person so nobody alone can fill it, one overall so everybody together cannot
// either.
const MAX_POST_IMAGES_PER_USER = 60;
const MAX_POST_IMAGES_TOTAL = 20000;

if (!PUBLIC_BASE_URL) {
  console.error('PUBLIC_BASE_URL is required so generated URLs are absolute.');
  process.exit(1);
}

fs.mkdirSync(AVATAR_DIR, { recursive: true });
fs.mkdirSync(POST_DIR, { recursive: true });

admin.initializeApp({ credential: admin.credential.applicationDefault() });
const db = admin.firestore();

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

/**
 * Post images are named `<uid>_<random>.jpg`. The uid prefix is what lets a
 * delete request be authorised without a database lookup in the common case,
 * and the random suffix is what stops one post's image being guessable from
 * another's.
 */
function parseImageId(imageId) {
  if (!/^[A-Za-z0-9_-]{1,160}$/.test(imageId)) return null;
  const split = imageId.lastIndexOf('_');
  if (split <= 0) return null;
  const uid = imageId.slice(0, split);
  const random = imageId.slice(split + 1);
  if (!/^[0-9a-f]{24}$/.test(random)) return null;
  if (!safeUid(uid)) return null;
  return { uid };
}

/** Resolves inside the directory, or not at all. */
function resolveWithin(dir, filename) {
  const target = path.join(dir, filename);
  if (path.dirname(path.resolve(target)) !== path.resolve(dir)) return null;
  return target;
}

/** Write to a temp name then rename, so a reader never sees a partial file. */
async function writeAtomic(target, buffer) {
  const temp = `${target}.tmp`;
  await fs.promises.writeFile(temp, buffer, { mode: 0o644 });
  await fs.promises.rename(temp, target);
}

function isUnreadableImage(err) {
  return err && err.message && /unsupported image|Input buffer/i.test(err.message);
}

app.get('/health', (req, res) => res.json({ ok: true }));

// ---------------------------------------------------------------- avatars

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
      .resize(AVATAR_PX, AVATAR_PX, { fit: 'cover', position: 'centre' })
      .jpeg({ quality: 85, mozjpeg: true })
      .toBuffer();

    const filename = `${uid}.jpg`;
    const target = resolveWithin(AVATAR_DIR, filename);
    if (!target) return res.status(400).json({ error: 'Bad path' });

    await writeAtomic(target, jpeg);

    // Cache-busting suffix: the filename is stable per user, so without it the
    // app and any CDN would keep showing the previous picture.
    res.json({ url: `${PUBLIC_BASE_URL}/avatars/${filename}?v=${Date.now()}` });
  } catch (err) {
    if (isUnreadableImage(err)) {
      return res.status(400).json({ error: 'That file is not a readable image' });
    }
    console.error('avatar upload failed', err);
    res.status(500).json({ error: 'Could not store the image' });
  }
});

// ------------------------------------------------------------ post images

app.post('/post-image', requireUser, upload.single('image'), async (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'No image supplied' });

  const uid = safeUid(req.user.uid);
  if (!uid) return res.status(400).json({ error: 'Unusable account id' });

  try {
    const existing = await fs.promises.readdir(POST_DIR);
    if (existing.length >= MAX_POST_IMAGES_TOTAL) {
      return res.status(507).json({ error: 'Photo storage is full' });
    }
    const mine = existing.filter((f) => f.startsWith(`${uid}_`)).length;
    if (mine >= MAX_POST_IMAGES_PER_USER) {
      return res.status(409).json({
        error: 'You have reached your photo limit. Delete an old post to add a new one.'
      });
    }

    // Unlike an avatar, a post photo is not a portrait: cropping it to a square
    // would cut the subject out of half of them. Fitted inside a box instead,
    // and never enlarged, so a small image is not blown up into mush.
    const jpeg = await sharp(req.file.buffer, { failOn: 'error' })
      .rotate()
      .resize(POST_MAX_PX, POST_MAX_PX, { fit: 'inside', withoutEnlargement: true })
      .jpeg({ quality: 82, mozjpeg: true })
      .toBuffer();

    const imageId = `${uid}_${crypto.randomBytes(12).toString('hex')}`;
    const filename = `${imageId}.jpg`;
    const target = resolveWithin(POST_DIR, filename);
    if (!target) return res.status(400).json({ error: 'Bad path' });

    await writeAtomic(target, jpeg);

    // No cache-buster: a post image is written once and never replaced, so the
    // URL is genuinely immutable and worth caching hard.
    res.json({ imageId, url: `${PUBLIC_BASE_URL}/post-images/${filename}` });
  } catch (err) {
    if (isUnreadableImage(err)) {
      return res.status(400).json({ error: 'That file is not a readable image' });
    }
    console.error('post image upload failed', err);
    res.status(500).json({ error: 'Could not store the image' });
  }
});

/**
 * Deleting is the half of this that is easy to get wrong. Two people are
 * allowed to remove a post photo: the person who uploaded it, and the admin of
 * the community it was posted in. The first is provable from the filename. The
 * second is not provable from anything the client sends, so it is checked
 * against Firestore -- a client claiming to be an admin proves nothing.
 */
app.delete('/post-image/:imageId', requireUser, async (req, res) => {
  const parsed = parseImageId(req.params.imageId);
  if (!parsed) return res.status(400).json({ error: 'Bad image id' });

  const caller = safeUid(req.user.uid);
  if (!caller) return res.status(400).json({ error: 'Unusable account id' });

  if (parsed.uid !== caller) {
    const cid = req.query.communityId;
    if (typeof cid !== 'string' || !/^[A-Za-z0-9_-]{1,64}$/.test(cid)) {
      return res.status(403).json({ error: 'Not yours to delete' });
    }
    try {
      const snapshot = await db.collection('communities').doc(cid).get();
      if (!snapshot.exists || snapshot.get('adminUid') !== caller) {
        return res.status(403).json({ error: 'Not yours to delete' });
      }
      // The admin is an admin somewhere -- but they must be the admin of a
      // community the uploader actually belongs to, or any admin anywhere could
      // delete any photo by naming their own group.
      const members = snapshot.get('memberUids') || [];
      if (!members.includes(parsed.uid)) {
        return res.status(403).json({ error: 'Not yours to delete' });
      }
    } catch (err) {
      console.error('admin check failed', err);
      return res.status(500).json({ error: 'Could not verify permission' });
    }
  }

  const target = resolveWithin(POST_DIR, `${req.params.imageId}.jpg`);
  if (!target) return res.status(400).json({ error: 'Bad image id' });

  try {
    await fs.promises.unlink(target);
    res.json({ ok: true });
  } catch (err) {
    // Already gone is the desired end state, so it is a success, not a 404.
    // Deletion gets retried after a failed post write and must be idempotent.
    if (err.code === 'ENOENT') return res.json({ ok: true });
    console.error('post image delete failed', err);
    res.status(500).json({ error: 'Could not delete the image' });
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
  console.log(`fittrack media service on 127.0.0.1:${PORT}`);
  console.log(`  avatars in ${AVATAR_DIR}`);
  console.log(`  post images in ${POST_DIR}`);
});
