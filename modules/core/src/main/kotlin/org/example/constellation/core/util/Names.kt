package org.example.constellation.core.util

object Names {
    /** `com/foo/Bar$Baz` -> `com.foo.Bar$Baz`. */
    fun toDotName(internal: String): String = internal.replace('/', '.')

    /** `com.foo.Bar` -> `com.foo`; `Bar` -> `""` (default package). */
    fun pkgOf(fqn: String): String = fqn.substringBeforeLast('.', "")

    /** `com.foo.Bar$Baz` -> `Bar$Baz`. */
    fun simpleNameOf(fqn: String): String = fqn.substringAfterLast('/')
        .substringAfterLast('.')
}
