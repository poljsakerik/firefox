/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// Open mode's page check, registered by background.js only while open mode is on, together with a user stylesheet that
// hides every page until its root element carries MirlumiPageCheckAttribute. The page can't remove that stylesheet, and
// doesn't know the attribute's name until it's shown.
//
// The script asks whether this load needs a check. If not, the page is shown at once. If so, it sends what the page
// says once the document has loaded, or as much as has loaded after CHECK_TIMEOUT_MS, and shows the page only when the
// background script clears that text. A flagged page loads again, and the background script sends that load to the
// blocked page. A check that fails or gets no answer within CHECK_TIMEOUT_MS sends the page to the "couldn't be
// checked" page the same way; nothing is shown unchecked.
//
// A cleared page keeps being watched. A change of address without a load, or enough new text, hides it again inside the
// MutationObserver callback, before anything is painted, and it's checked again. Each check carries a revision, and
// only the answer to the latest one counts. If a parent turns open mode off, the background script tells the page over
// its port, and the page loads again under allowlist-only browsing.

"use strict";

/* global MirlumiPageText, MirlumiPageCheckAttribute */

(function () {
  const CHECK_TIMEOUT_MS = 3000;
  const RECHECK_DELAY_MS = 150;
  const HIDE_AFTER_TEXT = 200;
  const root = document.documentElement;
  const token = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;

  let shown = false;
  const show = () => {
    shown = true;
    root.setAttribute(MirlumiPageCheckAttribute, "");
  };
  const hide = () => {
    shown = false;
    root.removeAttribute(MirlumiPageCheckAttribute);
  };
  // Some frameworks reset the root element's attributes as they start; a page meant to be shown gets it back.
  new MutationObserver(() => {
    if (shown && !root.hasAttribute(MirlumiPageCheckAttribute)) {
      root.setAttribute(MirlumiPageCheckAttribute, "");
    }
  }).observe(root, { attributes: true, attributeFilter: [MirlumiPageCheckAttribute] });

  let finished = false;
  let revision = 0;
  let cleared = null;
  let clearedAddress = location.href;
  let addedSinceClear = 0;
  let timeout = null;
  let recheck = null;

  const reload = () => location.replace(location.href.split("#")[0]);

  // A flagged or unchecked page loads again; the background script sends that load to the blocked page.
  function leave(type) {
    if (finished) return;
    finished = true;
    hide();
    clearTimeout(timeout);
    browser.runtime.sendMessage({ type, document: token }).then(reload, reload);
  }

  function check() {
    if (finished) return;
    const page = MirlumiPageText.readPage(document);
    const state = MirlumiPageText.pageState(location.href, page);
    if (cleared && !MirlumiPageText.changedEnough(cleared, state)) {
      addedSinceClear = 0;
      clearedAddress = location.href;
      show();
      return;
    }
    hide();
    const mine = ++revision;
    clearTimeout(timeout);
    timeout = setTimeout(() => leave("pageFailed"), CHECK_TIMEOUT_MS);
    browser.runtime.sendMessage({ type: "pageCheck", document: token, revision: mine, page }).then(
      (reply) => {
        if (finished || mine !== revision) return;
        clearTimeout(timeout);
        if (reply && reply.clear === mine) {
          cleared = state;
          clearedAddress = state.address;
          addedSinceClear = 0;
          show();
        } else if (reply && reply.blocked) {
          finished = true;
          hide();
          reload();
        } else {
          leave("pageFailed");
        }
      },
      () => {
        if (mine === revision) leave("pageFailed");
      }
    );
  }

  // Later changes don't push a scheduled check back, so a page that never stops changing is still checked.
  function scheduleCheck(delay) {
    if (recheck !== null) return;
    recheck = setTimeout(() => {
      recheck = null;
      check();
    }, delay);
  }

  // Once enough text arrived, or the address changed, the page is read right here, before it's painted. It's hidden
  // only if it changed enough to be checked again, so content that merely re-renders doesn't flicker.
  function watch() {
    new MutationObserver((records) => {
      if (finished || cleared === null) return;
      addedSinceClear += MirlumiPageText.textAdded(records);
      if (location.href === clearedAddress && addedSinceClear < HIDE_AFTER_TEXT) return;
      addedSinceClear = 0;
      const state = MirlumiPageText.pageState(location.href, MirlumiPageText.readPage(document));
      if (!MirlumiPageText.changedEnough(cleared, state)) {
        clearedAddress = location.href;
        return;
      }
      hide();
      scheduleCheck(RECHECK_DELAY_MS);
    }).observe(root, { subtree: true, childList: true, characterData: true });
    for (const event of ["popstate", "hashchange"]) {
      window.addEventListener(event, () => {
        hide();
        scheduleCheck(0);
      });
    }
  }

  timeout = setTimeout(() => leave("pageFailed"), CHECK_TIMEOUT_MS);
  browser.runtime.sendMessage({ type: "pageStart", document: token }).then(
    (reply) => {
      clearTimeout(timeout);
      if (reply && reply.revoke) {
        finished = true;
        reload();
        return;
      }
      if (reply && reply.check === false) {
        finished = true;
        show();
        return;
      }
      if (!reply || reply.check !== true) {
        leave("pageFailed");
        return;
      }
      const port = browser.runtime.connect({ name: "mirlumiPageCheck" });
      port.onMessage.addListener((message) => {
        if (message && message.type === "revoke") {
          finished = true;
          hide();
          reload();
        }
      });
      watch();
      if (document.readyState === "loading") {
        // A document still parsing after CHECK_TIMEOUT_MS is checked as far as it got; the rest is a change like any
        // other.
        document.addEventListener("DOMContentLoaded", check, { once: true });
        setTimeout(() => {
          if (revision === 0) check();
        }, CHECK_TIMEOUT_MS);
      } else {
        check();
      }
    },
    () => leave("pageFailed")
  );
})();
