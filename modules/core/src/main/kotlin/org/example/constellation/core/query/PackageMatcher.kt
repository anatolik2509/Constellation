package org.example.constellation.core.query

/**
 * Package patterns: a pattern selects the packages it matches **plus their subpackages**.
 * `com.foo` and `com.foo.*` are equivalent; `*` within a segment matches any
 * characters except `.` (so `com.foobar` never matches `com.foo`).
 */
object PackageMatcher {

    fun matches(pattern: String, pkg: String): Boolean {
        // Both `com.foo` and `com.foo.*` mean "the package and anything under it";
        // `com.foobar` must NOT match (segment boundary required).
        val base = pattern.removeSuffix(".*")
        val basePattern = toRegex(base)
        return basePattern.matches(pkg) || pkg.matches(Regex("${basePattern.pattern}\\..*"))
    }

    private fun toRegex(pattern: String): Regex {
        val sb = StringBuilder()
        for (ch in pattern) {
            if (ch == '*') sb.append("[^.]*") else sb.append(Regex.escape(ch.toString()))
        }
        return Regex(sb.toString())
    }
}
