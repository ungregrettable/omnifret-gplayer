package com.omnifret.gplayer.core.ecmaScript

// JS-shaped RegExp on top of Kotlin's KMP-stable kotlin.text.Regex. This
// replaces the library's JVM-only java.util.regex.Pattern wrapper. The
// transpiled code only uses .exec / .replace, so we keep the surface
// minimal; if the transpiler ever emits .test, .match, .matchAll, etc.
// add them here.

private data class RegExpCacheEntry(val pattern: String, val flags: String)

private val regexpCache = HashMap<RegExpCacheEntry, Regex>()

internal class RegExp {
    private val _regex: Regex
    private val _global: Boolean

    public constructor(pattern: String, flags: String = "") {
        var global = false
        val options = mutableSetOf<RegexOption>()
        for (c in flags) {
            when (c) {
                'i' -> options += RegexOption.IGNORE_CASE
                'm' -> options += RegexOption.MULTILINE
                // RegexOption.DOT_MATCHES_ALL is on every per-target stdlib but
                // isn't visible during the commonMain metadata compilation that
                // consumers (sibling modules, Compose MP composite-build) need.
                // Route it through expect/actual so metadata stays clean.
                's' -> options += dotMatchesAllRegexOption()
                'g' -> global = true
            }
        }
        _global = global
        _regex = regexpCache.getOrPut(RegExpCacheEntry(pattern, flags)) {
            Regex(pattern, options)
        }
    }

    public fun exec(s: String): Boolean = _regex.containsMatchIn(s)

    public fun replace(s: String, replacement: String): String {
        return if (_global)
            _regex.replace(s, Regex.escapeReplacement(replacement))
        else
            _regex.replaceFirst(s, Regex.escapeReplacement(replacement))
    }

    public fun replace(s: String, replacement: (match: String, group1: String) -> String): String {
        return replaceWith(s) { match ->
            val g1 = if (match.groupValues.size > 1) match.groupValues[1] else ""
            replacement(match.value, g1)
        }
    }

    public fun replace(s: String, replacement: (match: String) -> String): String {
        return replaceWith(s) { match -> replacement(match.value) }
    }

    private inline fun replaceWith(
        s: String,
        crossinline replacement: (MatchResult) -> String
    ): String {
        if (_global) {
            return _regex.replace(s) { match -> replacement(match) }
        }
        val match = _regex.find(s) ?: return s
        return s.substring(0, match.range.first) +
                replacement(match) +
                s.substring(match.range.last + 1)
    }
}
