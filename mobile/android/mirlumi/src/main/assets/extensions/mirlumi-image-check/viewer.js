/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// Shows an image that was opened on its own, which Gecko displays without running content scripts. The background
// script sends such loads here, with the image's address in the fragment, and the engine hides it until checked.
"use strict";

/* global mirlumiImageCheck, mirlumiCheckImage, MIRLUMI_IMAGE_CHECK */

mirlumiImageCheck(MIRLUMI_IMAGE_CHECK, mirlumiCheckImage);

const address = decodeURIComponent(location.hash.slice(1));
if (/^https?:\/\//i.test(address)) {
  document.title = address;
  document.addEventListener("DOMContentLoaded", () => {
    const img = document.createElement("img");
    img.src = address;
    img.alt = "";
    document.body.appendChild(img);
  });
}
