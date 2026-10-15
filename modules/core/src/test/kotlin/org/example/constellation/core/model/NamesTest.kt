package org.example.constellation.core.model

import org.example.constellation.core.util.Names
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NamesTest {

    @Test
    fun `dot name`() {
        assertEquals("com.foo.Bar", Names.toDotName("com/foo/Bar"))
    }

    @Test
    fun `package of`() {
        assertEquals("com.foo", Names.pkgOf("com.foo.Bar"))
        assertEquals("", Names.pkgOf("Bar"))
    }

    @Test
    fun `simple name keeps nested marker`() {
        assertEquals("Bar\$Baz", Names.simpleNameOf("com.foo.Bar\$Baz"))
        assertEquals("Bar", Names.simpleNameOf("Bar"))
    }
}
