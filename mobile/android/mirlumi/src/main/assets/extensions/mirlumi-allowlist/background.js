/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// Sends every top-level page that isn't allowed to the blocked page. The sites a parent allowed come from the app over
// a native port; until they arrive, pages not on the bundled list wait, for at most PARENT_SITES_TIMEOUT_MS.

"use strict";

/* global MirlumiAllowlist, MirlumiAllowlistSites */

const PORT_NAME = "mirlumiAllowlist";
const PARENT_SITES_TIMEOUT_MS = 5000;
const RECONNECT_MS = 1000;
const BLOCKED_PAGE = browser.runtime.getURL("generated/blocked.html");

let allowlist = MirlumiAllowlist.create([MirlumiAllowlistSites]);
let port = null;
let nextRequest = 1;
const pendingRequests = new Map();

let markParentSitesLoaded;
const parentSitesLoaded = new Promise((resolve) => {
  markParentSitesLoaded = resolve;
});
setTimeout(() => markParentSitesLoaded(), PARENT_SITES_TIMEOUT_MS);

function connect() {
  port = browser.runtime.connectNative(PORT_NAME);
  port.onMessage.addListener(onNativeMessage);
  port.onDisconnect.addListener(() => {
    port = null;
    for (const resolve of pendingRequests.values()) resolve({ granted: false, failed: true });
    pendingRequests.clear();
    setTimeout(connect, RECONNECT_MS);
  });
}

function onNativeMessage(message) {
  if (message.type === "parentSites" && Array.isArray(message.sites)) {
    allowlist = MirlumiAllowlist.create([MirlumiAllowlistSites, message.sites]);
    markParentSitesLoaded();
  } else if (message.type === "allowResult") {
    const resolve = pendingRequests.get(message.id);
    pendingRequests.delete(message.id);
    resolve?.({ granted: message.granted === true, failed: message.failed === true });
  }
}

function isAllowed(url) {
  const target = MirlumiAllowlist.hostAndPath(url);
  return target === null || allowlist.allows(target.host, target.path);
}

browser.webRequest.onBeforeRequest.addListener(
  async (details) => {
    if (isAllowed(details.url)) return {};
    await parentSitesLoaded;
    if (isAllowed(details.url)) return {};
    return { redirectUrl: `${BLOCKED_PAGE}#${encodeURIComponent(details.url)}` };
  },
  { urls: ["http://*/*", "https://*/*"], types: ["main_frame"] },
  ["blocking"]
);

// Only the blocked page, as a whole tab, can ask for the PIN.
browser.runtime.onMessage.addListener((message, sender) => {
  if (message?.type !== "allow" || sender.frameId > 0 || !sender.url?.startsWith(BLOCKED_PAGE)) {
    return undefined;
  }
  const target = MirlumiAllowlist.hostAndPath(message.url);
  const entry = target && MirlumiAllowlist.entryForHost(target.host);
  if (!entry || !port) return Promise.resolve({ granted: false, failed: true });
  const id = nextRequest++;
  return new Promise((resolve) => {
    pendingRequests.set(id, resolve);
    port.postMessage({ type: "allow", id, entry });
  });
});

connect();
