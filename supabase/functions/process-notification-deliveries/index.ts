import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import postgres from "https://deno.land/x/postgresjs@v3.4.5/mod.js";

const DB_URL = requiredEnv("SUPABASE_DB_URL");
const sql = postgres(DB_URL, { prepare: false, max: 1, idle_timeout: 10, connect_timeout: 10 });
const QUEUE = "notification_deliveries";
const MAX_ATTEMPTS = 8;
const BATCH_SIZE = 20;
const VISIBILITY_SECONDS = 120;
let cachedAccessToken: { token: string; expiresAt: number } | null = null;
let cachedServiceAccount: any | null = null;

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const expected = Deno.env.get("WEBHOOK_SECRET") ?? "";
  const provided = req.headers.get("x-cron-secret") ?? "";
  if (!expected || !constantTimeEqual(expected, provided)) return json({ error: "Unauthorized" }, 401);

  let jobs: any[];
  try {
    jobs = await sql`select msg_id, read_ct, enqueued_at, vt, message from pgmq.read(${QUEUE}, ${VISIBILITY_SECONDS}, ${BATCH_SIZE})`;
  } catch (error) {
    console.error("notification queue read failed", safeError(error));
    return json({ error: "Queue read failed" }, 500);
  }
  if (jobs.length === 0) return json({ ok: true, processed: 0 });

  const results: any[] = [];
  for (const job of jobs) {
    try {
      results.push(await processJob(job));
    } catch (error) {
      const message = safeError(error);
      console.error("notification delivery job failed", { msg_id: Number(job.msg_id), error: message });
      try {
        results.push(await safeRetry(job, message));
      } catch (retryError) {
        console.error("notification delivery retry bookkeeping failed", { msg_id: Number(job.msg_id), error: safeError(retryError) });
        results.push({ msg_id: Number(job.msg_id), outcome: "RETRY_DEFERRED" });
      }
    }
  }
  return json({ ok: true, processed: results.length, results });
});

async function processJob(job: any) {
  const msgId = Number(job.msg_id);
  const attemptNo = Math.max(1, Number(job.read_ct) || 1);
  const notificationId = String(job.message?.notification_id ?? "");
  if (!isUuid(notificationId)) {
    await archive(msgId);
    return { msg_id: msgId, outcome: "SKIPPED", reason: "invalid_notification_id" };
  }

  const rows = await sql`
    select id, org_id, user_id, target_user_id, audience, type, title, body,
           related_entity_id, related_entity_type, data, client_id
    from public.notifications where id = ${notificationId}::uuid limit 1
  `;
  const notification = rows[0];
  if (!notification) {
    await archive(msgId);
    return { msg_id: msgId, outcome: "SKIPPED", reason: "notification_missing" };
  }

  const recipientId = notification.target_user_id ?? notification.user_id;
  if (!recipientId) {
    await recordAttempt(notificationId, msgId, attemptNo, 0, 0, 0, 0, "DEAD", "missing recipient");
    await updateState(notificationId, msgId, attemptNo, "DEAD", "missing recipient", false);
    await archive(msgId);
    return { msg_id: msgId, outcome: "DEAD", reason: "missing_recipient" };
  }

  const tokens = await sql`
    select id, user_id, client_id, org_id, token, platform
    from public.push_tokens
    where user_id = ${String(recipientId)}::uuid and org_id = ${String(notification.org_id)}::uuid
    order by updated_at desc
  `;
  const receipts = await sql`
    select token_id, status from notification_delivery.device_receipts
    where notification_id = ${notificationId}::uuid
  `;
  const terminal = new Map(receipts.map((r: any) => [String(r.token_id), String(r.status)]));
  const candidates = tokens.filter((t: any) => !terminal.has(String(t.id)));
  const priorSent = receipts.some((r: any) => r.status === "SENT");

  if (candidates.length === 0) {
    const outcome = priorSent ? "SENT" : "NO_TOKEN";
    await recordAttempt(notificationId, msgId, attemptNo, 0, 0, 0, 0, outcome, null);
    await updateState(notificationId, msgId, attemptNo, outcome, null, outcome === "SENT");
    await archive(msgId);
    return { msg_id: msgId, outcome, attempted: 0 };
  }

  let accessToken: string;
  try {
    accessToken = await getFirebaseAccessToken();
  } catch (error) {
    return await retryOrDead(notificationId, msgId, attemptNo, 0, 0, candidates.length, 0, `FCM_AUTH:${safeError(error)}`);
  }

  const sends = await Promise.all(candidates.map((tokenRow: any) => sendFcm(accessToken, notification, tokenRow)));
  let succeeded = 0;
  let invalid = 0;
  let transientFailed = 0;
  const errors: string[] = [];

  for (let i = 0; i < sends.length; i++) {
    const result = sends[i];
    const tokenRow = candidates[i];
    if (result.ok) {
      succeeded += 1;
      await upsertReceipt(notificationId, String(tokenRow.id), "SENT", result.status, null);
      continue;
    }
    if (isInvalidToken(result)) {
      invalid += 1;
      await upsertReceipt(notificationId, String(tokenRow.id), "INVALID", result.status, result.code || null);
      await sql`delete from public.push_tokens where id = ${String(tokenRow.id)}::uuid`;
      continue;
    }
    transientFailed += 1;
    errors.push(`${result.status}:${result.code || "FCM_ERROR"}`);
  }

  const attempted = candidates.length;
  const totalSent = priorSent || succeeded > 0;
  if (transientFailed === 0) {
    const outcome = totalSent ? "SENT" : "NO_TOKEN";
    await recordAttempt(notificationId, msgId, attemptNo, attempted, succeeded, 0, invalid, outcome, null);
    await updateState(notificationId, msgId, attemptNo, outcome, null, outcome === "SENT");
    await archive(msgId);
    return { msg_id: msgId, outcome, attempted, succeeded, invalid };
  }

  return await retryOrDead(notificationId, msgId, attemptNo, attempted, succeeded, transientFailed, invalid, errors.slice(0, 8).join(","));
}

async function retryOrDead(notificationId: string, msgId: number, attemptNo: number, attempted: number, succeeded: number, failed: number, invalid: number, errorSummary: string) {
  const clipped = errorSummary.slice(0, 500);
  if (attemptNo >= MAX_ATTEMPTS) {
    await recordAttempt(notificationId, msgId, attemptNo, attempted, succeeded, failed, invalid, "DEAD", clipped);
    await updateState(notificationId, msgId, attemptNo, "DEAD", clipped, false);
    await archive(msgId);
    return { msg_id: msgId, outcome: "DEAD", attempted, succeeded, failed, invalid };
  }
  const delaySeconds = Math.min(3600, 30 * (2 ** Math.max(0, attemptNo - 1)));
  await recordAttempt(notificationId, msgId, attemptNo, attempted, succeeded, failed, invalid, "RETRY", clipped);
  await updateState(notificationId, msgId, attemptNo, "PENDING", clipped, false);
  await sql`select * from pgmq.set_vt(${QUEUE}, ${msgId}::bigint, ${delaySeconds})`;
  return { msg_id: msgId, outcome: "RETRY", retry_in_seconds: delaySeconds, attempted, succeeded, failed, invalid };
}

async function safeRetry(job: any, error: string) {
  const msgId = Number(job.msg_id);
  const attemptNo = Math.max(1, Number(job.read_ct) || 1);
  const notificationId = String(job.message?.notification_id ?? "");
  if (!isUuid(notificationId)) {
    await archive(msgId);
    return { msg_id: msgId, outcome: "SKIPPED", reason: "invalid_notification_id" };
  }
  const exists = await sql`select 1 from public.notifications where id = ${notificationId}::uuid limit 1`;
  if (exists.length === 0) {
    await archive(msgId);
    return { msg_id: msgId, outcome: "SKIPPED", reason: "notification_missing" };
  }
  return await retryOrDead(notificationId, msgId, attemptNo, 0, 0, 1, 0, error);
}

async function archive(msgId: number) { await sql`select pgmq.archive(${QUEUE}, ${msgId}::bigint)`; }

async function recordAttempt(notificationId: string, msgId: number, attemptNo: number, attempted: number, succeeded: number, failed: number, invalid: number, outcome: string, error: string | null) {
  await sql`
    insert into notification_delivery.delivery_attempts(
      notification_id, queue_msg_id, attempt_no, attempted_tokens,
      succeeded_tokens, failed_tokens, invalid_tokens, outcome, error_summary
    ) values (
      ${notificationId}::uuid, ${msgId}::bigint, ${attemptNo}, ${attempted},
      ${succeeded}, ${failed}, ${invalid}, ${outcome}, ${error}
    )
  `;
}

async function updateState(notificationId: string, msgId: number, attemptNo: number, status: string, error: string | null, delivered: boolean) {
  await sql`
    insert into notification_delivery.delivery_state(
      notification_id, queue_msg_id, status, attempt_count, last_error,
      enqueued_at, last_attempt_at, delivered_at, updated_at
    ) values (
      ${notificationId}::uuid, ${msgId}::bigint, ${status}, ${attemptNo}, ${error},
      now(), now(), ${delivered ? new Date().toISOString() : null}::timestamptz, now()
    )
    on conflict (notification_id) do update
    set queue_msg_id = excluded.queue_msg_id,
        status = excluded.status,
        attempt_count = excluded.attempt_count,
        last_error = excluded.last_error,
        last_attempt_at = now(),
        delivered_at = case when ${delivered} then now() else notification_delivery.delivery_state.delivered_at end,
        updated_at = now()
  `;
}

async function upsertReceipt(notificationId: string, tokenId: string, status: string, fcmStatus: number, code: string | null) {
  await sql`
    insert into notification_delivery.device_receipts(notification_id, token_id, status, fcm_status, fcm_code, sent_at)
    values (${notificationId}::uuid, ${tokenId}::uuid, ${status}, ${fcmStatus}, ${code}, now())
    on conflict (notification_id, token_id) do update
    set status = excluded.status, fcm_status = excluded.fcm_status, fcm_code = excluded.fcm_code, sent_at = now()
  `;
}

function deliveryPolicy(type: string, isAutoDrive: boolean) {
  if (isAutoDrive) return { priority: "HIGH" };
  switch (type) {
    case "PAYMENT_DUE_REMINDER":
    case "PAYMENT_OVERDUE":
    case "NEW_CHAT_MESSAGE":
    case "ADMIN_COMMISSION_NEEDED":
    case "WITHDRAWAL_REQUESTED":
    case "MARKETER_REGISTERED":
    case "AUTODRIVE_JOIN_REQUEST":
      return { priority: "HIGH" };
    default:
      return { priority: "NORMAL" };
  }
}

/**
 * Verto tokens receive data-only FCM so Android always renders the notification locally,
 * preserving the exact colored launcher icon in the shade and the monochrome equivalent
 * in the status bar. AutoDrive keeps the established notification+data payload contract.
 */
async function sendFcm(accessToken: string, notification: any, tokenRow: any) {
  const dataObj = notification.data && typeof notification.data === "object" ? notification.data : {};
  const navigationRoute = stringValue(dataObj.navigation_route) ?? stringValue(dataObj.nav_route) ?? stringValue(dataObj.route) ?? "";
  const isAutoDrive = Boolean(tokenRow.client_id);
  const policy = deliveryPolicy(String(notification.type ?? ""), isAutoDrive);
  const reserved = new Set([
    "notification_id", "type", "notification_type", "title", "body", "org_id", "client_id", "audience",
    "related_entity_id", "related_entity_type", "navigation_route", "route", "transport_type",
  ]);
  const extras = Object.fromEntries(Object.entries(dataObj).filter(([k]) => !reserved.has(k)).map(([k, v]) => [k, v == null ? "" : String(v)]));
  const canonicalType = String(notification.type ?? "notification");
  const transportType = canonicalType === "NEW_CHAT_MESSAGE" && String(notification.related_entity_type ?? "") === "INTERNAL_MESSAGE"
    ? "chat"
    : canonicalType;
  const title = String(notification.title ?? "إشعار");
  const body = String(notification.body ?? "").slice(0, 200);
  const canonicalData = compactStringRecord({
    ...extras,
    type: transportType,
    notification_type: canonicalType,
    title,
    body,
    notification_id: String(notification.id),
    org_id: String(notification.org_id ?? ""),
    client_id: String(notification.client_id ?? ""),
    audience: String(notification.audience ?? ""),
    related_entity_id: String(notification.related_entity_id ?? ""),
    related_entity_type: String(notification.related_entity_type ?? ""),
    navigation_route: navigationRoute,
    route: navigationRoute,
  });

  const message = isAutoDrive
    ? {
        token: tokenRow.token,
        notification: { title, body },
        data: canonicalData,
        android: { priority: "HIGH", notification: { channel_id: "autodrive", sound: "default" } },
      }
    : {
        token: tokenRow.token,
        data: canonicalData,
        android: { priority: policy.priority },
      };

  const sa = await loadServiceAccount();
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
    method: "POST",
    headers: { authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
    body: JSON.stringify({ message }),
  });
  if (response.ok) return { ok: true, status: response.status, code: "" };
  const text = await response.text();
  let code = "";
  try { const parsed = JSON.parse(text); code = parsed?.error?.details?.[0]?.errorCode ?? parsed?.error?.status ?? ""; } catch {}
  return { ok: false, status: response.status, code };
}

function isInvalidToken(result: any) { return !result.ok && (result.code === "UNREGISTERED" || result.code === "INVALID_ARGUMENT" || result.status === 404); }

async function getFirebaseAccessToken(): Promise<string> {
  if (cachedAccessToken && Date.now() < cachedAccessToken.expiresAt - 60_000) return cachedAccessToken.token;
  const sa = await loadServiceAccount();
  const now = Math.floor(Date.now() / 1000);
  const enc = new TextEncoder();
  const header = b64url(enc.encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claim = b64url(enc.encode(JSON.stringify({ iss: sa.client_email, scope: "https://www.googleapis.com/auth/firebase.messaging", aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600 })));
  const signingInput = `${header}.${claim}`;
  const key = await crypto.subtle.importKey("pkcs8", pemToPkcs8(sa.private_key), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, enc.encode(signingInput));
  const jwt = `${signingInput}.${b64url(signature)}`;
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }),
  });
  if (!response.ok) throw new Error(`firebase oauth failed:${response.status}`);
  const body = await response.json();
  if (!body?.access_token) throw new Error("firebase oauth response missing access_token");
  cachedAccessToken = { token: body.access_token, expiresAt: Date.now() + Number(body.expires_in ?? 3600) * 1000 };
  return cachedAccessToken.token;
}

async function loadServiceAccount() {
  if (cachedServiceAccount) return cachedServiceAccount;
  const rows = await sql`select decrypted_secret from vault.decrypted_secrets where name = 'firebase_service_account' limit 1`;
  const raw = String(rows[0]?.decrypted_secret ?? "");
  if (!raw) throw new Error("firebase service account missing from Vault");
  const parsed = JSON.parse(raw);
  cachedServiceAccount = typeof parsed === "string" ? JSON.parse(parsed) : parsed;
  if (!cachedServiceAccount?.project_id || !cachedServiceAccount?.client_email || !cachedServiceAccount?.private_key) throw new Error("invalid firebase service account in Vault");
  return cachedServiceAccount;
}

function requiredEnv(name: string) { const value = Deno.env.get(name); if (!value) throw new Error(`Missing environment variable:${name}`); return value; }
function safeError(error: unknown) { return (error instanceof Error ? error.message : String(error)).slice(0, 500); }
function constantTimeEqual(a: string, b: string) {
  const enc = new TextEncoder(); const aa = enc.encode(a), bb = enc.encode(b); const len = Math.max(aa.length, bb.length); let diff = aa.length ^ bb.length;
  for (let i = 0; i < len; i++) diff |= (aa[i] ?? 0) ^ (bb[i] ?? 0); return diff === 0;
}
function isUuid(value: string) { return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value); }
function compactStringRecord(values: Record<string, unknown>) {
  const out: Record<string, string> = {}; for (const [key, value] of Object.entries(values)) { const text = value == null ? "" : String(value); if (text) out[key] = text.slice(0, 1000); } return out;
}
function stringValue(value: unknown) { return typeof value === "string" && value.trim() ? value.trim() : null; }
function pemToPkcs8(pem: string) {
  const b64 = pem.replace(/-----BEGIN PRIVATE KEY-----/, "").replace(/-----END PRIVATE KEY-----/, "").replace(/\s+/g, ""); const bin = atob(b64); const bytes = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i); return bytes.buffer;
}
function b64url(buf: ArrayBuffer | Uint8Array) {
  const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf); let binary = ""; for (const byte of bytes) binary += String.fromCharCode(byte); return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}
function json(body: unknown, status = 200) { return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }); }
