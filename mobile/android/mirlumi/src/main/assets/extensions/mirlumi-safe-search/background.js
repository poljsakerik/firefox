/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/* global MirlumiSafeSearch */

"use strict";

browser.webRequest.onBeforeRequest.addListener(
  details => {
    // A posted form: its search is in the body, and a redirect would post the same body again.
    if (MirlumiSafeSearch.refusesLoad(details.url, details.method)) {
      return { cancel: true };
    }
    const redirectUrl = MirlumiSafeSearch.safeSearchUrl(details.url);
    return redirectUrl ? { redirectUrl } : {};
  },
  {
    urls: MirlumiSafeSearch.REQUEST_PATTERNS,
    types: ["main_frame", "sub_frame"],
  },
  ["blocking"]
);
