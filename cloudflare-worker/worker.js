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

async function verifyFirebaseIdToken(idToken) {
  const parts = idToken.split(".");
  if (parts.length !== 3) throw new Error("Invalid Firebase ID token");
  const header = JSON.parse(atob(parts[0].replace(/-/g, "+").replace(/_/g, "/")));
  const claims = JSON.parse(atob(parts[1].replace(/-/g, "+").replace(/_/g, "/")));
  if (!header.kid || claims.aud !== PROJECT_ID || claims.iss !== "https://securetoken.google.com/" + PROJECT_ID ||
      !claims.sub || claims.sub.length > 128 || claims.exp <= Math.floor(Date.now() / 1000)) {
    throw new Error("Invalid Firebase ID token claims");
  }
  const jwksResponse = await fetch("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com");
  if (!jwksResponse.ok) throw new Error("Could not load Firebase signing keys");
  const jwks = await jwksResponse.json();
  const jwk = jwks.keys.find(key => key.kid === header.kid);
  if (!jwk) throw new Error("Unknown Firebase signing key");
  const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const signature = Uint8Array.from(atob(parts[2].replace(/-/g, "+").replace(/_/g, "/")), c => c.charCodeAt(0));
  const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, signature, new TextEncoder().encode(parts[0] + "." + parts[1]));
  if (!valid) throw new Error("Invalid Firebase ID token signature");
  return claims;
}


async function syncLatestAppRelease(env) {
  if (!env.FIREBASE_SERVICE_ACCOUNT) throw new Error("Missing FIREBASE_SERVICE_ACCOUNT secret");
  const serviceAccount = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT);
  const token = await googleAccessToken(serviceAccount);
  const response = await fetch("https://api.github.com/repos/aqsinkerimov2025-arch/TEXNO-TELEKOM/releases/latest", {
    headers: { "accept": "application/vnd.github+json", "user-agent": "TEXNO-TELEKOM-release-sync" }
  });
  if (response.status === 404) return { synced: false, reason: "no_release" };
  const release = await response.json();
  if (!response.ok) throw new Error("GitHub release lookup failed: " + JSON.stringify(release));
  const tag = String(release.tag_name || "").slice(0, 120);
  if (!tag) return { synced: false, reason: "missing_tag" };
  const documentId = tag.replace(/[^A-Za-z0-9_-]/g, "_");
  const documentPath = "app_notifications/auto_release_" + documentId;
  try {
    await firestoreRequest(documentPath, token);
    return { synced: false, reason: "already_exists", tag };
  } catch (error) {
    if (!String(error).includes("404")) throw error;
  }
  const title = String(release.name || "TEXNO-TELEKOM yeniləndi").slice(0, 160);
  const notes = String(release.body || "").trim();
  const body = (notes || ("Yeni versiya yayımlandı: " + tag)) .slice(0, 3000);
  await firestoreRequest(documentPath, token, {
    method: "PATCH",
    body: JSON.stringify({ fields: {
      title: { stringValue: title },
      body: { stringValue: body },
      type: { stringValue: "app_version" },
      version: { stringValue: tag },
      release_tag: { stringValue: tag },
      created_at: { timestampValue: new Date().toISOString() }
    }})
  });
  return { synced: true, tag };
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
        data: { title, body, type: fromValue(fields.type) || "update", content_type: fromValue(fields.content_type) || fromValue(fields.type) || "program", program_id: fromValue(fields.program_id) || "" },
        android: { priority: "HIGH", ttl: "2419200s" }
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
  async fetch(request, env, ctx) {
    if (request.method === "GET") {
      return new Response("TEXNO-TELEKOM push worker is running.", { headers: { "content-type": "text/plain; charset=utf-8" } });
    }
    if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
    try {
      const payload = await request.json();
      if (typeof payload.idToken !== "string") return new Response("Unauthorized", { status: 401 });
      await verifyFirebaseIdToken(payload.idToken);
      const releaseSync = await syncLatestAppRelease(env);
      const result = await runPush(env);
      result.releaseSync = releaseSync;
      return Response.json({ ok: true, ...result });
    } catch (error) {
      return Response.json({ ok: false, error: String(error) }, { status: 500 });
    }
  },
  async scheduled(_event, env, ctx) {
    ctx.waitUntil((async () => {
      await syncLatestAppRelease(env);
      await runPush(env);
    })());
  }
};
