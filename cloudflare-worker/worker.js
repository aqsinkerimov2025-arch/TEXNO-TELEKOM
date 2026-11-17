const PROJECT_ID = "texno-telekom";
const STATE_PATH = "app_notifications_push_state/latest";

function b64url(input) {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : new Uint8Array(input);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function pemToArrayBuffer(pem) {
  const body = pem.replace(/-----BEGIN PRIVATE KEY-----/g, "").replace(/-----END PRIVATE KEY-----/g, "").replace(/\s/g, "");
  const binary = atob(body);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}

async function googleAccessToken(serviceAccount) {
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = b64url(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600
  }));
  const unsigned = header + "." + claim;
  const key = await crypto.subtle.importKey(
    "pkcs8", pemToArrayBuffer(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  const assertion = unsigned + "." + b64url(signature);
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion
    })
  });
  const data = await response.json();
  if (!response.ok) throw new Error("Google OAuth failed: " + JSON.stringify(data));
  return data.access_token;
}

async function firestoreRequest(path, token, options = {}) {
  const response = await fetch("https://firestore.googleapis.com/v1/projects/" + PROJECT_ID + "/databases/(default)/documents/" + path, {
    ...options,
    headers: { authorization: "Bearer " + token, "content-type": "application/json", ...(options.headers || {}) }
  });
  const text = await response.text();
  if (!response.ok) throw new Error("Firestore " + response.status + ": " + text);
  return text ? JSON.parse(text) : {};
}

function fromValue(value) {
  if (!value) return "";
  if (value.stringValue !== undefined) return value.stringValue;
  if (value.timestampValue !== undefined) return value.timestampValue;
  return "";
}

async function runPush(env) {
  if (!env.FIREBASE_SERVICE_ACCOUNT) throw new Error("Missing FIREBASE_SERVICE_ACCOUNT secret");
  const serviceAccount = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT);
  const token = await googleAccessToken(serviceAccount);
  let state;
  try {
    state = await firestoreRequest(STATE_PATH, token);
  } catch (error) {
    if (!String(error).includes("404")) throw error;
    const now = new Date().toISOString();
    await firestoreRequest(STATE_PATH, token, {
      method: "PATCH",
      body: JSON.stringify({ fields: { last_sent_at: { timestampValue: now } } })
    });
    return { initialized: true, sent: 0 };
  }

  const lastSent = fromValue(state.fields?.last_sent_at);
  const query = {
    structuredQuery: {
      from: [{ collectionId: "app_notifications" }],
      where: { fieldFilter: {
        field: { fieldPath: "created_at" },
        op: "GREATER_THAN",
        value: { timestampValue: lastSent }
      }},
      orderBy: [{ field: { fieldPath: "created_at" }, direction: "ASCENDING" }],
      limit: 100
    }
  };
  const response = await fetch("https://firestore.googleapis.com/v1/projects/" + PROJECT_ID + "/databases/(default)/documents:runQuery", {
    method: "POST",
    headers: { authorization: "Bearer " + token, "content-type": "application/json" },
    body: JSON.stringify(query)
  });
  const rows = await response.json();
  if (!response.ok) throw new Error("Firestore query failed: " + JSON.stringify(rows));
  const docs = rows.map(row => row.document).filter(Boolean);
  if (!docs.length) return { initialized: false, sent: 0 };

  let latest = lastSent;
  let sent = 0;
  for (const doc of docs) {
    const fields = doc.fields || {};
    const title = fromValue(fields.title) || "TEXNO-TELEKOM yeniliyi";
    const body = fromValue(fields.body) || "Yeni proqram və ya oyun əlavə edildi.";
    const created = fromValue(fields.created_at);
    const message = {
      message: {
        topic: "texno_updates",
        notification: { title, body },
        data: { type: fromValue(fields.type) || "update" },
        android: { priority: "HIGH", notification: { channel_id: "texno_updates", sound: "default" } }
      }
    };
    const fcm = await fetch("https://fcm.googleapis.com/v1/projects/" + PROJECT_ID + "/messages:send", {
      method: "POST",
      headers: { authorization: "Bearer " + token, "content-type": "application/json" },
      body: JSON.stringify(message)
    });
    const result = await fcm.json();
    if (!fcm.ok) throw new Error("FCM send failed: " + JSON.stringify(result));
    latest = created || latest;
    sent++;
  }

  await firestoreRequest(STATE_PATH, token, {
    method: "PATCH",
    body: JSON.stringify({ fields: { last_sent_at: { timestampValue: latest } } })
  });
  return { initialized: false, sent };
}

export default {
  async fetch() {
    return new Response("TEXNO-TELEKOM push worker is running.", { headers: { "content-type": "text/plain; charset=utf-8" } });
  },
  async scheduled(_event, env, ctx) {
    ctx.waitUntil(runPush(env));
  }
};
