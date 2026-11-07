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
// A cleared page keeps being watched. When most of its text is replaced, as when a single page app shows another page,
// it's hidden inside the MutationObserver callback, before anything is painted, and checked again. A change of address
// without a load, as when a search box writes what's typed into the address, or a smaller change of text, is checked
// while the page stays in view, shortly after the changes stop, and the page is blocked if it's flagged. So is a
// change that would hide the page while the reader types, since that's suggestions for what they type. Each check
// carries a revision, and only the answer to the latest one counts. A check of a cleared page that fails is tried
// again; if it still fails, a page in view stays as it is, and a hidden one goes to the "couldn't be checked" page.
// If a parent turns open mode off, the background script tells the page over its port, and the page loads again under
// allowlist-only browsing.

"use strict";

/* global MirlumiPageText, MirlumiPageCheckAttribute */

(function () {
  const CHECK_TIMEOUT_MS = 3000;
  const RECHECK_DELAY_MS = 150;
  const HIDE_AFTER_TEXT = 200;
  const ATTRIBUTE_READ_MS = 100;
  const SMALL_CHANGE_DELAY_MS = 300;
  const IN_VIEW_DELAY_MS = 400;
  const IN_VIEW_MAX_DELAY_MS = 1500;
  const RETRIES = 2;
  const RETRY_DELAY_MS = 500;
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
    } else if (!shown && root.hasAttribute(MirlumiPageCheckAttribute)) {
      root.removeAttribute(MirlumiPageCheckAttribute);
    }
  }).observe(root, { attributes: true, attributeFilter: [MirlumiPageCheckAttribute] });

  let finished = false;
  let revision = 0;
  let cleared = null;
  let clearedAddress = location.href;
  let addedSinceClear = 0;
  let timeout = null;
  let recheck = null;
  let inViewCheck = null;
  let inViewDue = 0;
  let failures = 0;
  let failedRevision = 0;
  let typedAt = -Infinity;

  // A frame without an address of its own, such as srcdoc, can't load again: it stays hidden, and its page is blocked.
  const reload = () => {
    if (/^https?:$/.test(location.protocol)) location.replace(location.href.split("#")[0]);
  };

  // A flagged or unchecked page loads again; the background script sends that load to the blocked page.
  function leave(type) {
    if (finished) return;
    finished = true;
    hide();
    clearTimeout(timeout);
    browser.runtime.sendMessage({ type, document: token }).then(reload, reload);
  }

  // How the page changed since `before`: a change that would hide it is checked in view while the reader types.
  function changeKind(before, after) {
    const kind = MirlumiPageText.changeKind(before, after);
    return kind === "hide" && Date.now() - typedAt < MirlumiPageText.TYPING_MS ? "check" : kind;
  }

  // Checks the page as it is now. `inView` keeps a shown page in view while it's checked; otherwise it's hidden until
  // it's cleared.
  function check(inView) {
    if (finished) return;
    cancelInViewCheck();
    const page = MirlumiPageText.readPage(document);
    const state = MirlumiPageText.pageState(location.href, page);
    if (cleared && !MirlumiPageText.changedEnough(cleared, state)) {
      addedSinceClear = 0;
      clearedAddress = location.href;
      show();
      return;
    }
    inView = inView === true && shown;
    if (!inView) hide();
    const mine = ++revision;
    clearTimeout(timeout);
    timeout = setTimeout(() => failed(mine, inView), CHECK_TIMEOUT_MS);
    const message = { type: "pageCheck", document: token, revision: mine, page, address: state.address };
    browser.runtime.sendMessage(message).then(
      (reply) => {
        if (finished || mine !== revision) return;
        clearTimeout(timeout);
        if (reply && reply.clear === mine) {
          failures = 0;
          const current = MirlumiPageText.pageState(location.href, MirlumiPageText.readPage(document));
          const changed = MirlumiPageText.changedEnough(state, current);
          if (changed && !inView) {
            check(false);
            return;
          }
          cleared = state;
          clearedAddress = state.address;
          addedSinceClear = 0;
          show();
          if (changed) compareToCleared();
        } else if (reply && reply.blocked) {
          finished = true;
          hide();
          reload();
        } else {
          failed(mine, inView);
        }
      },
      () => failed(mine, inView)
    );
  }

  // A check got no answer in time, or a failed one. The first check of a page fails closed. A later one is tried again,
  // and if it still fails, a page in view stays as it is until it changes again; a hidden one isn't shown unchecked.
  function failed(mine, inView) {
    if (finished || mine !== revision || mine === failedRevision) return;
    failedRevision = mine;
    clearTimeout(timeout);
    if (cleared === null) {
      leave("pageFailed");
    } else if (failures < RETRIES) {
      failures++;
      setTimeout(() => {
        if (!finished && mine === revision) check(inView);
      }, RETRY_DELAY_MS);
    } else if (inView) {
      failures = 0;
    } else {
      leave("pageFailed");
    }
  }

  // Later changes don't push a scheduled check back, so a page that never stops changing is still checked.
  function scheduleCheck(delay) {
    if (recheck !== null) return;
    recheck = setTimeout(() => {
      recheck = null;
      check(false);
    }, delay);
  }

  // A check in view runs IN_VIEW_DELAY_MS after the last change, and no later than IN_VIEW_MAX_DELAY_MS after the
  // first, so a page that never stops changing is still checked.
  function scheduleInViewCheck() {
    const now = Date.now();
    if (inViewCheck === null) {
      inViewDue = now + IN_VIEW_MAX_DELAY_MS;
    } else {
      clearTimeout(inViewCheck);
    }
    inViewCheck = setTimeout(() => {
      inViewCheck = null;
      if (!finished && recheck === null) check(true);
    }, Math.min(IN_VIEW_DELAY_MS, inViewDue - now));
  }

  function cancelInViewCheck() {
    clearTimeout(inViewCheck);
    inViewCheck = null;
  }

  // Reads the page and compares it with what was cleared. A page most of whose text is new is hidden right here: this
  // runs inside the MutationObserver callback, so nothing of it is painted first. A smaller change, or a new address,
  // is checked in view. Content that merely re-renders changes nothing.
  function compareToCleared() {
    addedSinceClear = 0;
    const state = MirlumiPageText.pageState(location.href, MirlumiPageText.readPage(document));
    const kind = changeKind(cleared, state);
    clearedAddress = location.href;
    if (kind === "none") return;
    if (kind === "hide" || !shown) {
      hide();
      cancelInViewCheck();
      scheduleCheck(RECHECK_DELAY_MS);
      return;
    }
    scheduleInViewCheck();
  }

  // Attribute changes can reveal hidden text, but animations make them constantly. A change on elements holding real
  // text is compared at once; others at most once every ATTRIBUTE_READ_MS, with one read after the last change.
  let lastAttributeRead = 0;
  let attributeRead = null;
  function attributeChanged() {
    if (attributeRead !== null) return;
    const wait = lastAttributeRead + ATTRIBUTE_READ_MS - Date.now();
    const run = () => {
      attributeRead = null;
      lastAttributeRead = Date.now();
      if (!finished && cleared !== null && recheck === null) compareToCleared();
    };
    if (wait <= 0) {
      run();
    } else {
      attributeRead = setTimeout(run, wait);
    }
  }

  // Text changes too small to hide the page at once, such as a new title, are still compared once they settle.
  let smallChangeRead = null;
  function smallChange() {
    if (smallChangeRead !== null) return;
    smallChangeRead = setTimeout(() => {
      smallChangeRead = null;
      if (!finished && cleared !== null && recheck === null) compareToCleared();
    }, SMALL_CHANGE_DELAY_MS);
  }

  function watch() {
    new MutationObserver((records) => {
      if (finished || cleared === null) return;
      records = records.filter((record) => record.type !== "attributes" || record.attributeName !== MirlumiPageCheckAttribute);
      if (!records.length) return;
      addedSinceClear += MirlumiPageText.textAdded(records);
      if (
        location.href !== clearedAddress ||
        addedSinceClear >= HIDE_AFTER_TEXT ||
        MirlumiPageText.attributeText(records) >= HIDE_AFTER_TEXT
      ) {
        compareToCleared();
      } else if (records.some((record) => record.type === "attributes")) {
        attributeChanged();
      } else {
        smallChange();
      }
    }).observe(root, { subtree: true, childList: true, characterData: true, attributes: true });
    for (const event of ["popstate", "hashchange"]) {
      window.addEventListener(event, () => {
        if (!finished && cleared !== null && recheck === null) compareToCleared();
      });
    }
    for (const event of ["keydown", "input"]) {
      window.addEventListener(
        event,
        (e) => {
          if (e.isTrusted) typedAt = Date.now();
        },
        true
      );
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
        document.addEventListener("DOMContentLoaded", () => check(false), { once: true });
        setTimeout(() => {
          if (revision === 0) check(false);
        }, CHECK_TIMEOUT_MS);
      } else {
        check(false);
      }
    },
    () => leave("pageFailed")
  );
})();
