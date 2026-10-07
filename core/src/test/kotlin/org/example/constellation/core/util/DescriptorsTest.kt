package org.example.constellation.core.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class DescriptorsTest {

    @Test
    fun `no-arg void method`() {
        assertEquals("run(): void", Descriptors.formatMethod("run", "()V"))
    }

    @Test
    fun `primitives and arrays`() {
        assertEquals("zip(String, int[]): int", Descriptors.formatMethod("zip", "(Ljava/lang/String;[I)I"))
    }

    @Test
    fun `nested class keeps dollar segment`() {
        assertEquals("visit(Bar\$Baz): void", Descriptors.formatMethod("visit", "(Lcom/foo/Bar\$Baz;)V"))
    }

    @Test
    fun `nested arrays and long`() {
        assertEquals("m(long[][]): boolean", Descriptors.formatMethod("m", "([[J)Z"))
    }

    @Test
    fun `malformed descriptor falls back to raw`() {
        assertEquals("broken(??)", Descriptors.formatMethod("broken", "??"))
    }

    @Test
    fun `type formatting`() {
        assertEquals("String", Descriptors.formatType("Ljava/lang/String;"))
        assertEquals("int[]", Descriptors.formatType("[I"))
        assertEquals("Object[]", Descriptors.formatType("[Ljava/lang/Object;"))
        assertEquals("void", Descriptors.formatType("V"))
    }
}

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
