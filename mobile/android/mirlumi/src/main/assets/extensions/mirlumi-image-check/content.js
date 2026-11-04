/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// Runs in every frame at document start, after generated/engine.js, generated/config.js and check.js. The engine
// hides each image until check.js has had it judged.
"use strict";

/* global mirlumiImageCheck, mirlumiCheckImage, MIRLUMI_IMAGE_CHECK */

mirlumiImageCheck(MIRLUMI_IMAGE_CHECK, mirlumiCheckImage);
