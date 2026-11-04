/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// Downloads newer signed rules and keeps them in storage, where content.js reads them. Runs after generated/rules.js
// and generated/validate.js. A failed download or a document that doesn't verify changes nothing: pages keep the
// rules they have, and the bundled rules are always there underneath.
"use strict";

const REFRESH_ALARM = "refresh";
const REFRESH_MINUTES = 6 * 60;

function bytes(base64) {
  return Uint8Array.from(atob(base64), c => c.charCodeAt(0));
}

/** The body of [response], or an error once it passes [limit] bytes. */
async function readAtMost(response, limit) {
  const reader = response.body.getReader();
  const chunks = [];
  let length = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    length += value.length;
    if (length > limit) {
      reader.cancel();
      throw new Error("too large");
    }
    chunks.push(value);
  }
  const body = new Uint8Array(length);
  let offset = 0;
  for (const chunk of chunks) {
    body.set(chunk, offset);
    offset += chunk.length;
  }
  return body;
}

/**
 * The validated rules in a signed `{payload, signature}` document: base64 of the rules JSON, and base64 of its
 * ECDSA P-256 / SHA-256 signature in the raw 64-byte form. Throws unless the signature holds and the rules are valid.
 */
async function verify(signed, publicKey) {
  const text = new TextDecoder("utf-8", { fatal: true, ignoreBOM: true }).decode(signed);
  const document = JSON.parse(text);
  if (
    !document ||
    typeof document !== "object" ||
    Object.keys(document).sort().join() !== "payload,signature" ||
    typeof document.payload !== "string" ||
    typeof document.signature !== "string"
  ) {
    throw new Error("not a signed rules document");
  }
  const key = await crypto.subtle.importKey(
    "spki",
    bytes(publicKey),
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["verify"]
  );
  const payload = bytes(document.payload);
  const valid = await crypto.subtle.verify(
    { name: "ECDSA", hash: "SHA-256" },
    key,
    bytes(document.signature),
    payload
  );
  if (!valid) {
    throw new Error("bad signature");
  }
  // ignoreBOM keeps a byte order mark in the text, where JSON.parse refuses it.
  return validateContentRules(
    new TextDecoder("utf-8", { fatal: true, ignoreBOM: true }).decode(payload)
  );
}

async function refresh() {
  try {
    const config = await (
      await fetch(browser.runtime.getURL("generated/config.json"))
    ).json();
    // No cookies, no cache and no redirects: the request carries nothing about the person, and only this URL counts.
    const response = await fetch(config.url, {
      cache: "no-store",
      credentials: "omit",
      redirect: "error",
    });
    if (!response.ok) {
      return;
    }
    const validated = await verify(
      await readAtMost(response, MIRLUMI_CONTENT_RULES_MAX_LENGTH),
      config.publicKey
    );
    const stored = (await browser.storage.local.get("rules")).rules;
    const current = Math.max(
      MIRLUMI_BUNDLED_RULES.version,
      stored ? stored.version : 0
    );
    // Only ever move forward, so an older signed file served again can't bring back rules that were replaced.
    if (validated.version > current) {
      await browser.storage.local.set({ rules: validated.rules });
    }
  } catch (e) {
    console.warn(`Mirlumi content rules not updated: ${e.message}`);
  }
}

function schedule() {
  browser.alarms.create(REFRESH_ALARM, { periodInMinutes: REFRESH_MINUTES });
  refresh();
}

browser.runtime.onStartup.addListener(schedule);
browser.runtime.onInstalled.addListener(schedule);
browser.alarms.onAlarm.addListener(alarm => {
  if (alarm.name === REFRESH_ALARM) {
    refresh();
  }
});
