/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// Passes each image a content script needs checked to the app over a native port, and the app's answer back. An
// animated image goes as several of its frames, decoded here, where WebCodecs' ImageDecoder is available even when the
// page isn't a secure context. Pages and frames that are an image on their own go to viewer.html, because Gecko runs no
// content scripts in them.
"use strict";

/* global MIRLUMI_IMAGE_CHECK */

const PORT_NAME = "mirlumiImageCheck";
const VIEWER = browser.runtime.getURL("viewer.html");

let port = null;
let nextId = 1;
const waiting = new Map();

function connect() {
  port = browser.runtime.connectNative(PORT_NAME);
  port.onMessage.addListener((message) => {
    const resolve = message && waiting.get(message.id);
    if (!resolve) return;
    waiting.delete(message.id);
    resolve(message.result);
  });
  port.onDisconnect.addListener(() => {
    port = null;
    for (const resolve of waiting.values()) resolve({ error: "the app disconnected" });
    waiting.clear();
  });
}

function ask(data, expires) {
  if (!port) connect();
  const id = nextId++;
  return new Promise((resolve) => {
    waiting.set(id, resolve);
    port.postMessage({ type: "check", id, data, expires });
  });
}

function dataUrl(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}

/** The first bytes of a base64 data: URL, without decoding the rest. */
function head(data) {
  const comma = data.indexOf(",");
  if (comma === -1 || !/;base64$/i.test(data.slice(0, comma))) return "";
  try {
    return atob(data.slice(comma + 1, comma + 1 + 44));
  } catch (e) {
    return "";
  }
}

/** The animated format the bytes are in, whatever type the server or page named, or null. */
function animatedType(text) {
  if (text.startsWith("GIF8")) return "image/gif";
  if (text.startsWith("RIFF") && text.slice(8, 12) === "WEBP") return "image/webp";
  if (text.startsWith("\x89PNG")) return "image/png";
  if (text.slice(4, 12) === "ftypavis") return "image/avif";
  return null;
}

/** Up to maxFrames frames, spread from the first to the last, as JPEG data: URLs; null for a still image. */
async function frames(data) {
  if (typeof ImageDecoder !== "function") return null;
  try {
    const type = animatedType(head(data));
    if (!type) return null;
    const bytes = new Uint8Array(await (await fetch(data)).arrayBuffer());
    const decoder = new ImageDecoder({ data: bytes, type });
    await decoder.tracks.ready;
    // The frame count grows while the decoder reads the file.
    await decoder.completed;
    const count = decoder.tracks.selectedTrack ? decoder.tracks.selectedTrack.frameCount : 1;
    if (count <= 1) return null;
    const n = Math.min(MIRLUMI_IMAGE_CHECK.maxFrames, count);
    const urls = [];
    for (let i = 0; i < n; i++) {
      const { image } = await decoder.decode({ frameIndex: Math.floor((i * (count - 1)) / (n - 1)) });
      const canvas = new OffscreenCanvas(image.displayWidth, image.displayHeight);
      canvas.getContext("2d").drawImage(image, 0, 0);
      image.close();
      urls.push(await dataUrl(await canvas.convertToBlob({ type: "image/jpeg", quality: 0.9 })));
    }
    decoder.close();
    return urls;
  } catch (e) {
    return null;
  }
}

async function check(data, expires) {
  const animation = await frames(data);
  if (!animation) return ask(data, expires);
  // engine.js decides on the frames' answers together.
  return { frames: await Promise.all(animation.map((frame) => ask(frame, expires))) };
}

browser.runtime.onMessage.addListener((message) => {
  if (!message || message.type !== "check" || typeof message.data !== "string") return undefined;
  const expires = Number.isFinite(message.expires) ? message.expires : Date.now() + 20000;
  return check(message.data, expires);
});

browser.webRequest.onHeadersReceived.addListener(
  (details) => {
    const type = (details.responseHeaders || []).find((h) => h.name.toLowerCase() === "content-type");
    const value = type && type.value ? type.value.toLowerCase() : "";
    const image = (value.startsWith("image/") && !value.includes("svg")) || value.startsWith("multipart/x-mixed-replace");
    if (!image) return {};
    return { redirectUrl: `${VIEWER}#${encodeURIComponent(details.url)}` };
  },
  { urls: ["http://*/*", "https://*/*"], types: ["main_frame", "sub_frame", "object"] },
  ["blocking", "responseHeaders"]
);

connect();
