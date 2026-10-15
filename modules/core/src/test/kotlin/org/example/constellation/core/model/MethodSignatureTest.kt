package org.example.constellation.core.model

import org.example.constellation.core.analyze.internal.DescriptorParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MethodSignatureTest {

    @Test
    fun `no-arg void method`() {
        assertEquals("run(): void", DescriptorParser.parseMethod("run", "()V").render())
    }

    @Test
    fun `primitives and arrays`() {
        assertEquals(
            "zip(String, int[]): int",
            DescriptorParser.parseMethod("zip", "(Ljava/lang/String;[I)I").render(),
        )
    }

    @Test
    fun `nested class keeps dollar segment`() {
        assertEquals(
            "visit(Bar\$Baz): void",
            DescriptorParser.parseMethod("visit", "(Lcom/foo/Bar\$Baz;)V").render(),
        )
    }

    @Test
    fun `nested arrays and long`() {
        assertEquals("m(long[][]): boolean", DescriptorParser.parseMethod("m", "([[J)Z").render())
    }

    @Test
    fun `type refs preserve the full class name in the model`() {
        val sig = DescriptorParser.parseMethod("zip", "(Ljava/lang/String;)Ljava/lang/String;")
        val param = sig.params.single() as TypeRef.Class
        assertEquals("java.lang.String", param.fqn)
        assertEquals("String", param.render())
    }

    @Test
    fun `malformed descriptor stays well-typed`() {
        val sig = DescriptorParser.parseMethod("broken", "??")
        assertTrue(sig.render().startsWith("broken("))
    }
}
