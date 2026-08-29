'use strict';

/**
 * Wipes FitTrack back to an empty state for testing.
 *
 * Deletes every Firebase Auth account, every community, every user's synced
 * data, and optionally the uploaded images on disk. There is no undo and no
 * backup: run it only on a project you are willing to lose.
 *
 * It lists what it would delete and stops, unless you pass --confirm. That is
 * deliberate -- the whole point of this file is that it is dangerous.
 *
 *   node reset-data.js                  # show what would go
 *   node reset-data.js --confirm        # actually delete
 *   node reset-data.js --confirm --images   # and clear the image folders
 */

const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');

const CONFIRM = process.argv.includes('--confirm');
const WITH_IMAGES = process.argv.includes('--images');
const AVATAR_DIR = process.env.AVATAR_DIR || '/var/lib/fittrack/avatars';
const POST_DIR = process.env.POST_DIR || '/var/lib/fittrack/posts';

admin.initializeApp({ credential: admin.credential.applicationDefault() });
const db = admin.firestore();
const auth = admin.auth();

/** Every account, a page at a time; listUsers caps at 1000 per call. */
async function allUsers() {
  const users = [];
  let pageToken;
  do {
    const page = await auth.listUsers(1000, pageToken);
    users.push(...page.users);
    pageToken = page.pageToken;
  } while (pageToken);
  return users;
}

async function main() {
  const users = await allUsers();
  const communities = await db.collection('communities').get();

  console.log('');
  console.log(`  accounts    : ${users.length}`);
  users.slice(0, 20).forEach((u) => {
    console.log(`      ${u.uid}  ${u.email || '(no email)'}  ${u.displayName || ''}`);
  });
  if (users.length > 20) console.log(`      ... and ${users.length - 20} more`);

  console.log(`  communities : ${communities.size}`);
  communities.docs.slice(0, 20).forEach((d) => {
    console.log(`      ${d.id}  ${d.get('name')}  (${d.get('memberCount')} members)`);
  });

  if (WITH_IMAGES) {
    console.log(`  avatars     : ${countFiles(AVATAR_DIR)} files in ${AVATAR_DIR}`);
    console.log(`  post images : ${countFiles(POST_DIR)} files in ${POST_DIR}`);
  }
  console.log('');

  if (!CONFIRM) {
    console.log('  Nothing deleted. Re-run with --confirm to actually do it.');
    console.log('  There is no undo.');
    return;
  }

  // Communities first. Their subcollections -- members, requests, scores,
  // posts and each post's comments and reactions -- are not removed by
  // deleting the parent, so each tree is taken down recursively.
  for (const doc of communities.docs) {
    await db.recursiveDelete(doc.ref);
    console.log(`  deleted community ${doc.id}`);
  }

  // Then each account's private mirror, which lives under users/{uid} and is
  // likewise a tree rather than a single document.
  for (const user of users) {
    await db.recursiveDelete(db.collection('users').doc(user.uid));
  }
  console.log(`  deleted synced data for ${users.length} accounts`);

  // Accounts last: while they exist, a device still holding a valid token can
  // write something back and repopulate what was just cleared.
  const uids = users.map((u) => u.uid);
  for (let i = 0; i < uids.length; i += 1000) {
    const result = await auth.deleteUsers(uids.slice(i, i + 1000));
    console.log(`  deleted ${result.successCount} accounts, ${result.failureCount} failed`);
    result.errors.forEach((e) => console.log(`      ${e.index}: ${e.error.message}`));
  }

  if (WITH_IMAGES) {
    clearDir(AVATAR_DIR);
    clearDir(POST_DIR);
  }

  console.log('');
  console.log('  Done. Sign in on the app to start again from nothing.');
}

function countFiles(dir) {
  try {
    return fs.readdirSync(dir).length;
  } catch (err) {
    return 0;
  }
}

/** Removes the contents, never the directory: its owner and mode matter. */
function clearDir(dir) {
  let removed = 0;
  try {
    for (const name of fs.readdirSync(dir)) {
      fs.unlinkSync(path.join(dir, name));
      removed++;
    }
  } catch (err) {
    console.log(`  could not clear ${dir}: ${err.message}`);
    return;
  }
  console.log(`  cleared ${removed} files from ${dir}`);
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error('reset failed', err);
    process.exit(1);
  });
