// supabase/functions/notify-sos/index.ts
//
// Викликається тригером sos_requests_notify (pg_net, AFTER INSERT ON sos_requests).
// Надсилає push волонтерам (profiles.purpose_role = 'volunteer'), у яких є
// токен у device_tokens.
//
// Налаштування (через Dashboard, без CLI):
//   1. Edge Functions -> Create a new function -> назва "notify-sos" -> вставити цей код -> Deploy.
//   2. Edge Functions -> notify-sos -> Details -> вимкнути "Enforce JWT Verification"
//      (тригер БД викликає функцію без користувацького JWT).
//   3. Project Settings -> Edge Functions -> Secrets (або Edge Functions -> notify-sos -> Secrets)
//      -> додати FCM_SERVICE_ACCOUNT зі значенням = повний вміст JSON-файлу сервісного акаунта.
//
// Легасі FCM "Server Key" (Authorization: key=...) Google повністю вимкнув у червні
// 2024 — той підхід більше не працює. Актуальний FCM HTTP v1 API вимагає OAuth2
// access token, отриманий через сервісний акаунт Firebase: Firebase Console ->
// Project settings -> Service accounts -> Generate new private key. Саме цей
// JSON-файл (не "Server Key") і потрібно покласти в секрет FCM_SERVICE_ACCOUNT.
//
// SUPABASE_URL і SUPABASE_SERVICE_ROLE_KEY Supabase підставляє автоматично —
// їх не потрібно створювати вручну як секрети.

interface ServiceAccount {
  client_email: string;
  private_key: string;
  project_id: string;
}

interface SosRecord {
  id?: string;
  lat?: number;
  lng?: number;
  problem_type?: string;
}

const PROBLEM_LABELS: Record<string, string> = {
  obstacle: "Перешкода на шляху",
  physical_help: "Потрібна фізична допомога",
  other: "Хтось поруч потребує допомоги"
};

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s/g, "");
  const raw = atob(b64);
  const bytes = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
  return bytes.buffer;
}

function base64url(input: ArrayBuffer | string): string {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : new Uint8Array(input);
  let str = "";
  for (const b of bytes) str += String.fromCharCode(b);
  return btoa(str).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function getAccessToken(account: ServiceAccount): Promise<string> {
  const header = { alg: "RS256", typ: "JWT" };
  const now = Math.floor(Date.now() / 1000);
  const claim = {
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600
  };

  const unsigned = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(claim))}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(account.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  const jwt = `${unsigned}.${base64url(signature)}`;

  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt
    })
  });
  const data = await response.json();
  if (!data.access_token) throw new Error(`OAuth token exchange failed: ${JSON.stringify(data)}`);
  return data.access_token as string;
}

async function fetchVolunteerFcmTokens(supabaseUrl: string, serviceRoleKey: string): Promise<string[]> {
  const authHeaders = {
    apikey: serviceRoleKey,
    Authorization: `Bearer ${serviceRoleKey}`
  };

  const volunteersRes = await fetch(
    `${supabaseUrl}/rest/v1/profiles?select=id&purpose_role=eq.volunteer`,
    { headers: authHeaders }
  );
  if (!volunteersRes.ok) throw new Error(`profiles query failed: ${await volunteersRes.text()}`);
  const volunteers = (await volunteersRes.json()) as { id: string }[];
  if (volunteers.length === 0) return [];

  const idsList = volunteers.map((v) => v.id).join(",");
  const tokensRes = await fetch(
    `${supabaseUrl}/rest/v1/device_tokens?select=fcm_token&user_id=in.(${idsList})`,
    { headers: authHeaders }
  );
  if (!tokensRes.ok) throw new Error(`device_tokens query failed: ${await tokensRes.text()}`);
  const tokens = (await tokensRes.json()) as { fcm_token: string }[];
  return tokens.map((t) => t.fcm_token);
}

Deno.serve(async (req) => {
  try {
    const serviceAccountRaw = Deno.env.get("FCM_SERVICE_ACCOUNT");
    if (!serviceAccountRaw) {
      return new Response(JSON.stringify({ error: "FCM_SERVICE_ACCOUNT secret is not set" }), { status: 500 });
    }
    const serviceAccount: ServiceAccount = JSON.parse(serviceAccountRaw);

    const payload = await req.json();
    const record = payload.record as SosRecord | undefined;
    if (!record) {
      return new Response(JSON.stringify({ error: "no record in payload" }), { status: 400 });
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

    const fcmTokens = await fetchVolunteerFcmTokens(supabaseUrl, serviceRoleKey);
    if (fcmTokens.length === 0) {
      return new Response(JSON.stringify({ sent: 0 }), { headers: { "Content-Type": "application/json" } });
    }

    const accessToken = await getAccessToken(serviceAccount);
    const body = PROBLEM_LABELS[record.problem_type ?? ""] ?? PROBLEM_LABELS.other;

    let sent = 0;
    for (const token of fcmTokens) {
      const response = await fetch(
        `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json"
          },
          body: JSON.stringify({
            message: {
              token,
              notification: { title: "Потрібна допомога поруч", body },
              data: {
                type: "sos_request",
                sos_id: String(record.id ?? ""),
                lat: String(record.lat ?? ""),
                lng: String(record.lng ?? "")
              }
            }
          })
        }
      );
      if (response.ok) sent++;
    }

    return new Response(JSON.stringify({ sent }), { headers: { "Content-Type": "application/json" } });
  } catch (e) {
    return new Response(JSON.stringify({ error: e instanceof Error ? e.message : String(e) }), { status: 500 });
  }
});
