/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

package com.omnifret.gplayer.core.ecmaScript

/**
 * Returns [RegexOption.DOT_MATCHES_ALL] from each target's stdlib. The
 * common metadata compiler doesn't see this constant (Kotlin 2.2 quirk),
 * so we route through expect/actual to keep the metadata compilation
 * green for sibling modules and composite-build consumers.
 */
internal expect fun dotMatchesAllRegexOption(): RegexOption
