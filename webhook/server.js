const express = require("express");
const fs = require("fs/promises");
const path = require("path");
require("dotenv").config();

const app = express();

const HOST = process.env.HOST || "0.0.0.0";
const PORT = Number(process.env.PORT) || 3000;
const WEBHOOK_PATH = process.env.WEBHOOK_PATH || "/webhook";
const BEARER_TOKEN = process.env.WEBHOOK_BEARER_TOKEN || "";
const LOG_FILE = process.env.WEBHOOK_LOG_FILE || path.join(process.cwd(), "logs", "webhook.log");
const JSON_LIMIT = process.env.JSON_LIMIT || "1mb";

app.use(express.json({ limit: JSON_LIMIT }));

async function appendLog(logEntry) {
  const logDir = path.dirname(LOG_FILE);
  await fs.mkdir(logDir, { recursive: true });
  await fs.appendFile(LOG_FILE, `${JSON.stringify(logEntry)}\n`, "utf8");
}

function requireBearerAuth(req, res, next) {
  if (!BEARER_TOKEN) {
    return next();
  }

  const authHeader = req.get("authorization") || "";
  if (!authHeader.startsWith("Bearer ")) {
    return res.status(401).json({
      ok: false,
      message: "Authorization header must use Bearer <token> format.",
    });
  }

  const token = authHeader.slice(7).trim();
  if (token !== BEARER_TOKEN) {
    return res.status(403).json({
      ok: false,
      message: "Invalid bearer token.",
    });
  }

  return next();
}

app.get("/health", (req, res) => {
  res.json({ ok: true, service: "webhook-api" });
});

app.post(WEBHOOK_PATH, requireBearerAuth, async (req, res) => {
  try {
    const { authorization: _authorization, ...safeHeaders } = req.headers;

    const entry = {
      receivedAt: new Date().toISOString(),
      method: req.method,
      path: req.originalUrl,
      ip: req.ip,
      headers: safeHeaders,
      body: req.body,
    };

    await appendLog(entry);

    return res.status(200).json({
      ok: true,
      message: "Webhook received.",
    });
  } catch (error) {
    return res.status(500).json({
      ok: false,
      message: "Failed to write webhook log.",
      error: error.message,
    });
  }
});

app.use((err, req, res, next) => {
  if (err instanceof SyntaxError && "body" in err) {
    return res.status(400).json({
      ok: false,
      message: "Invalid JSON body.",
    });
  }

  return next(err);
});

app.listen(PORT, HOST, () => {
  console.log(`Webhook API running on http://${HOST}:${PORT}`);
  console.log(`Endpoint webhook: POST ${WEBHOOK_PATH}`);
  console.log(BEARER_TOKEN ? "Bearer auth: enabled" : "Bearer auth: disabled (no token configured)");
  console.log(`Log file: ${LOG_FILE}`);
});
