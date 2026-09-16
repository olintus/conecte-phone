import { readFile } from "node:fs/promises";
import { createSign, randomUUID } from "node:crypto";

function options(argv) {
  const result = {};
  for (let index = 0; index < argv.length; index += 2) {
    const key = argv[index];
    if (!key?.startsWith("--") || argv[index + 1] == null) {
      throw new Error(`Argumento inválido: ${key ?? ""}`);
    }
    result[key.slice(2)] = argv[index + 1];
  }
  return result;
}

function base64url(value) {
  return Buffer.from(JSON.stringify(value)).toString("base64url");
}

async function accessToken(credentials) {
  const now = Math.floor(Date.now() / 1000);
  const header = base64url({ alg: "RS256", typ: "JWT" });
  const claims = base64url({
    iss: credentials.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: credentials.token_uri ?? "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  });
  const unsigned = `${header}.${claims}`;
  const signer = createSign("RSA-SHA256");
  signer.update(unsigned);
  signer.end();
  const assertion = `${unsigned}.${signer.sign(credentials.private_key).toString("base64url")}`;

  const response = await fetch(credentials.token_uri ?? "https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  const body = await response.json();
  if (!response.ok) throw new Error(`Falha OAuth (${response.status}): ${JSON.stringify(body)}`);
  return body.access_token;
}

async function main() {
  const args = options(process.argv.slice(2));
  const credentialPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (!credentialPath) throw new Error("Defina GOOGLE_APPLICATION_CREDENTIALS.");
  if (!args.token) throw new Error("Informe --token TOKEN_FCM.");

  const credentials = JSON.parse(await readFile(credentialPath, "utf8"));
  const type = args.type ?? "incoming_call";
  if (!["incoming_call", "call_cancelled", "call_ended"].includes(type)) {
    throw new Error(`Tipo não suportado: ${type}`);
  }

  const callId = args["call-id"] ?? randomUUID();
  const data = type === "incoming_call"
    ? {
        type,
        callId,
        displayName: args.name ?? "Teste Conecte",
        handle: args.number ?? "1000",
        accountId: args.account ?? "teste",
        issuedAt: new Date().toISOString(),
      }
    : { type, callId };

  const token = await accessToken(credentials);
  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${encodeURIComponent(credentials.project_id)}/messages:send`,
    {
      method: "POST",
      headers: {
        authorization: `Bearer ${token}`,
        "content-type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token: args.token,
          data,
          android: {
            priority: "HIGH",
            ttl: type === "incoming_call" ? "45s" : "10s",
          },
        },
      }),
    },
  );
  const body = await response.json();
  if (!response.ok) throw new Error(`Falha FCM (${response.status}): ${JSON.stringify(body)}`);
  console.log(`Push ${type} enviado. callId=${callId} message=${body.name}`);
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
