/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// Sends every top-level page that isn't allowed to the blocked page. The sites a parent allowed come from the app over
// a native port; until they arrive, pages not on the bundled list wait, for at most PARENT_SITES_TIMEOUT_MS.
//
// In open mode, which a parent turns on in Settings, pages that aren't allowed open instead, and the page classifier
// from packages/page-classifier checks them: page-check.js keeps each such page hidden until its text is cleared, and
// checks it again when it changes without loading. A page it flags, or can't check in time, goes to the blocked page
// with the reason. DuckDuckGo and Wikipedia searches it flags are blocked before they load, and a search a page writes
// into its address is judged with the page. A flagged page is blocked for the rest of the session, and so is its whole
// site once its home page or three of its pages are flagged (MirlumiAllowlist.createFlags), unless a parent allows it.
// Frames are checked like pages, each on its own: a flagged frame gets the blocked page in its place, and a frame
// without an address of its own, such as srcdoc, is judged with the page that holds it.

"use strict";

/* global MirlumiAllowlist, MirlumiAllowlistSites, MirlumiPageClassifier */

const PORT_NAME = "mirlumiAllowlist";
const PARENT_SITES_TIMEOUT_MS = 5000;
const RECONNECT_MS = 1000;
const BLOCKED_PAGE = browser.runtime.getURL("generated/blocked.html");
const MODEL_URL = browser.runtime.getURL("generated/page-classifier.mpc");

let allowlist = MirlumiAllowlist.create([MirlumiAllowlistSites]);
let port = null;
let nextRequest = 1;
const pendingRequests = new Map();

// Open mode is on only once page-check.js is registered; until then, and if registering fails, pages are judged as in
// allowlist-only browsing. Mode changes run one after another, so a late registration can't turn a later "off" back on.
let openMode = false;
let modeChange = Promise.resolve();
let model = null;
let pageCheckScript = null;
// The attribute that shows a checked page, named afresh at every start so a page can't know it in advance.
const SHOWN_ATTRIBUTE = `data-mirlumi-${crypto.randomUUID().replace(/-/g, "")}`;
// The document being checked in each tab, with its latest revision; tabs whose check failed, with the address; the
// open pages' ports; and the pages and sites flagged this session.
const documents = new Map();
const uncheckedFrames = new Map();
const pagePorts = new Set();
const flags = MirlumiAllowlist.createFlags();

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
  if (message.type === "mode") {
    setOpenMode(message.open === true);
  } else if (message.type === "parentSites" && Array.isArray(message.sites)) {
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

function blockedPage(url, reason) {
  return `${BLOCKED_PAGE}${reason ? `?reason=${reason}` : ""}#${encodeURIComponent(url)}`;
}

// The model loads once, when open mode first needs it, and stays loaded. A failed load is retried on next use.
function loadModel() {
  model ??= fetch(MODEL_URL)
    .then((response) => response.arrayBuffer())
    .then((buffer) => MirlumiPageClassifier.loadModel(new Uint8Array(buffer)))
    .catch((error) => {
      model = null;
      throw error;
    });
  return model;
}

function setOpenMode(open) {
  modeChange = modeChange.then(() => applyOpenMode(open));
}

async function applyOpenMode(open) {
  openMode = false;
  if (open) {
    loadModel().catch(() => {});
    try {
      pageCheckScript ??= await browser.contentScripts.register({
        matches: ["http://*/*", "https://*/*"],
        css: [{ code: `html:not([${SHOWN_ATTRIBUTE}]) { opacity: 0 !important; pointer-events: none !important; }` }],
        cssOrigin: "user",
        js: [
          { code: `var MirlumiPageCheckAttribute = ${JSON.stringify(SHOWN_ATTRIBUTE)};` },
          { file: "generated/page-text.js" },
          { file: "page-check.js" },
        ],
        runAt: "document_start",
        allFrames: true,
        matchAboutBlank: true,
      });
      openMode = true;
    } catch (e) {
      pageCheckScript = null;
    }
    return;
  }
  documents.clear();
  // Pages opened in open mode load again, now under allowlist-only browsing.
  for (const pagePort of pagePorts) pagePort.postMessage({ type: "revoke" });
  const registered = pageCheckScript;
  pageCheckScript = null;
  await registered?.unregister().catch(() => {});
}

// Checks are tracked per frame. A frame without an http address, such as about:srcdoc, belongs to the page holding it.
const frameKey = (tabId, frameId) => `${tabId}:${frameId}`;
const senderKey = (sender) => frameKey(sender.tab.id, sender.frameId);
const ownAddress = (url) => /^https?:/i.test(url ?? "");
const pageUrl = (sender) => (ownAddress(sender.url) ? sender.url : sender.tab?.url ?? "");

function forgetTab(tabId) {
  for (const key of [...documents.keys()]) {
    if (key.startsWith(`${tabId}:`)) documents.delete(key);
  }
}

// The label the page at url, or its site, was flagged with this session, or null.
function flaggedLabel(url) {
  const target = MirlumiAllowlist.hostAndPath(url);
  return target && flags.label(target.host, target.path, target.query);
}

// In open mode: the blocked page for a flagged search or a page or site flagged earlier, or null to let the load go on.
async function openModeBlock(url) {
  const query = MirlumiPageClassifier.searchQuery(url);
  if (query !== null) {
    try {
      const { flagged } = MirlumiPageClassifier.checkSearch(await loadModel(), query);
      return flagged ? blockedPage(url, `search:${flagged}`) : null;
    } catch (e) {
      return blockedPage(url, "search:unchecked");
    }
  }
  const flagged = flaggedLabel(url);
  return flagged ? blockedPage(url, flagged) : null;
}

browser.webRequest.onBeforeRequest.addListener(
  async (details) => {
    forgetTab(details.tabId);
    const unchecked = uncheckedFrames.get(frameKey(details.tabId, 0));
    uncheckedFrames.delete(frameKey(details.tabId, 0));
    if (unchecked === details.url.split("#")[0] && !isAllowed(details.url)) {
      return { redirectUrl: blockedPage(details.url, "unchecked") };
    }
    // The mode arrives just before the parent's sites, and a mode change finishes before anything is decided by it.
    if (MirlumiPageClassifier.searchQuery(details.url) !== null) {
      await parentSitesLoaded;
      await modeChange;
      if (openMode) {
        const redirectUrl = await openModeBlock(details.url);
        return redirectUrl ? { redirectUrl } : {};
      }
    }
    if (isAllowed(details.url)) return {};
    await parentSitesLoaded;
    await modeChange;
    if (isAllowed(details.url)) return {};
    if (!openMode) return { redirectUrl: blockedPage(details.url) };
    const redirectUrl = await openModeBlock(details.url);
    return redirectUrl ? { redirectUrl } : {};
  },
  { urls: ["http://*/*", "https://*/*"], types: ["main_frame"] },
  ["blocking"]
);

// In open mode, a frame of a flagged site, or one whose check failed, gets the blocked page in its place. Allowlist-only
// browsing leaves frames alone, as before.
browser.webRequest.onBeforeRequest.addListener(
  async (details) => {
    const key = frameKey(details.tabId, details.frameId);
    documents.delete(key);
    const unchecked = uncheckedFrames.get(key);
    uncheckedFrames.delete(key);
    await modeChange;
    if (!openMode || isAllowed(details.url)) return {};
    if (unchecked === details.url.split("#")[0]) return { redirectUrl: blockedPage(details.url, "unchecked") };
    const flagged = flaggedLabel(details.url);
    return flagged ? { redirectUrl: blockedPage(details.url, flagged) } : {};
  },
  { urls: ["http://*/*", "https://*/*"], types: ["sub_frame"] },
  ["blocking"]
);

// page-check.js reports at the start of every page while it's registered. A page that isn't allowed becomes the tab's
// document under check, named by the token the script chose. If open mode went off since the page was requested, the
// page loads again under allowlist-only browsing.
async function startPage(token, sender) {
  await modeChange;
  const url = pageUrl(sender);
  if (isAllowed(url)) return { check: false };
  if (!openMode || typeof token !== "string" || flaggedLabel(url)) return { revoke: true };
  documents.set(senderKey(sender), { token, revision: 0 });
  return { check: true };
}

// Checks what page-check.js read, for the tab's document under check, at a revision newer than any before. The answer
// names the revision it clears. A flagged page is remembered, and page-check.js loads the page again, which the
// request listener sends to the blocked page: GeckoView only lets an extension change a tab through the app, which
// built-in extensions don't get, and a page can't open an extension page itself.
// The address a check judges: the one the page shows now, which a single page app can change without loading, on the
// host it loaded from, since it can't change that.
function checkedUrl(message, sender) {
  const url = pageUrl(sender);
  const loaded = MirlumiAllowlist.hostAndPath(url);
  const shown = MirlumiAllowlist.hostAndPath(message.address);
  return loaded && shown && loaded.host === shown.host ? message.address : url;
}

async function checkPage(message, sender) {
  const key = senderKey(sender);
  const url = checkedUrl(message, sender);
  const current = documents.get(key);
  if (!openMode || !current || current.token !== message.document) return { failed: true };
  if (!Number.isInteger(message.revision) || message.revision <= current.revision) return { stale: true };
  current.revision = message.revision;
  const page = message.page;
  const text = (value, max) => String(value ?? "").slice(0, max);
  const fields = {
    title: text(page?.title, 1000),
    description: text(page?.description, 2000),
    headings: Array.isArray(page?.headings) ? page.headings.slice(0, 30).map((h) => text(h, 300)) : [],
    body: text(page?.body, 8000),
  };
  const { flagged } = MirlumiPageClassifier.checkPageAt(await loadModel(), fields, url);
  if (documents.get(key) !== current || current.revision !== message.revision) return { stale: true };
  // Another page may have flagged this one, or its site, while it was checked.
  if (!flagged) return flaggedLabel(url) ? { blocked: true } : { clear: message.revision };
  const target = MirlumiAllowlist.hostAndPath(url);
  if (!target || !flags.flag(target.host, target.path, target.query, flagged)) return { failed: true };
  documents.delete(key);
  revokeFlaggedPages();
  return { blocked: true };
}

// Open pages flagged since they were cleared, or of a site flagged since, load again, and the request listener blocks
// them.
function revokeFlaggedPages() {
  for (const pagePort of pagePorts) {
    if (flaggedLabel(pageUrl(pagePort.sender))) {
      documents.delete(senderKey(pagePort.sender));
      pagePort.postMessage({ type: "revoke" });
    }
  }
}

// The page's check timed out or failed: its next load goes to the "couldn't be checked" page.
function pageFailed(message, sender) {
  const key = senderKey(sender);
  if (documents.get(key)?.token === message.document || !documents.has(key)) {
    documents.delete(key);
    uncheckedFrames.set(key, sender.url.split("#")[0]);
  }
  return {};
}

// page-check.js, in every frame of a page, reports its start, sends what it read, and reports failures.
browser.runtime.onMessage.addListener((message, sender) => {
  if (!sender.tab || sender.url?.startsWith(BLOCKED_PAGE)) return undefined;
  switch (message?.type) {
    case "pageStart":
      return startPage(message.document, sender);
    case "pageCheck":
      return checkPage(message, sender).catch(() => ({ failed: true }));
    case "pageFailed":
      return Promise.resolve(pageFailed(message, sender));
    default:
      return undefined;
  }
});

// A checked page keeps a port open, so turning open mode off can make it load again.
browser.runtime.onConnect.addListener((pagePort) => {
  if (pagePort.name !== "mirlumiPageCheck" || !pagePort.sender?.tab) return;
  if (!openMode) {
    pagePort.postMessage({ type: "revoke" });
    return;
  }
  pagePorts.add(pagePort);
  pagePort.onDisconnect.addListener(() => pagePorts.delete(pagePort));
});

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
