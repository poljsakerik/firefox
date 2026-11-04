/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// Runs in each top frame at document start, after generated/engine.js and generated/rules.js. The engine starts once
// it's known which rules count, the bundled ones or newer downloaded ones: starting with the bundled rules first would
// already have redirected a page that newer rules allow. Rules arriving later replace them in pages already open. Only
// background.js writes storage, and only rules that passed validate.js.
"use strict";

let appliedVersion = 0;

/** Runs the engine with [rules] unless rules at least as new already run: a later update can arrive before the read. */
function applyIfNewer(rules) {
  if (rules && rules.version > appliedVersion) {
    appliedVersion = rules.version;
    mirlumiContentRules(rules);
  }
}

function start(stored) {
  applyIfNewer(stored && stored.version > MIRLUMI_BUNDLED_RULES.version ? stored : MIRLUMI_BUNDLED_RULES);
}

browser.storage.onChanged.addListener((changes, area) => {
  if (area === "local" && changes.rules) {
    const rules = changes.rules.newValue;
    if (rules && rules.version > MIRLUMI_BUNDLED_RULES.version) {
      applyIfNewer(rules);
    }
  }
});
browser.storage.local.get("rules").then(
  stored => start(stored.rules),
  () => start(null)
);
