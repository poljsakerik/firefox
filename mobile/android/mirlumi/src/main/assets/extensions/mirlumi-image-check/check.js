/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// The engine's check(request) for this extension: reads the image and asks the app through the background script.
// Content scripts and viewer.html both use it, after generated/engine.js and generated/config.js.
"use strict";

/* exported mirlumiCheckImage */
/* global MIRLUMI_IMAGE_CHECK */


function mirlumiDataUrl(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}

function mirlumiAsk(data) {
  return browser.runtime.sendMessage({
    type: "check",
    data,
    expires: Date.now() + MIRLUMI_IMAGE_CHECK.timeoutMs,
  });
}

async function mirlumiCheck(request) {
  if (request.data) return mirlumiAsk(request.data);
  // A content script's fetch reads other sites' images with the extension's host permissions. It goes through the
  // browser's cache, which usually still holds the image the page just loaded, with the tab's cookies and privacy.
  const response = await fetch(request.url, { cache: "force-cache", credentials: "include" });
  if (!response.ok) return { error: `HTTP ${response.status}` };
  const blob = await response.blob();
  if (blob.size > MIRLUMI_IMAGE_CHECK.maxBytes) return { error: "too large" };
  return mirlumiAsk(await mirlumiDataUrl(blob));
}

function mirlumiCheckImage(request) {
  return mirlumiCheck(request).catch((e) => ({ error: String(e) }));
}
