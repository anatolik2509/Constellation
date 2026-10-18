package org.example.constellation.core.analyze.internal

import org.example.constellation.core.api.model.MethodTrait
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MethodTraitsTest {

    @Test
    fun `constructor shape`() {
        assertEquals(setOf(MethodTrait.CONSTRUCTOR), MethodTraits.ofCall("<init>", "()V"))
        assertEquals(setOf(MethodTrait.CONSTRUCTOR), MethodTraits.ofCall("<init>", "(Ljava/lang/String;)V"))
    }

    @Test
    fun `getter shapes`() {
        assertEquals(setOf(MethodTrait.GETTER), MethodTraits.ofCall("getX", "()I"))
        assertEquals(setOf(MethodTrait.GETTER), MethodTraits.ofCall("getName", "()Ljava/lang/String;"))
        assertEquals(setOf(MethodTrait.GETTER), MethodTraits.ofCall("isReady", "()Z"))
    }

    @Test
    fun `getter near-misses`() {
        // params disqualify
        assertEquals(emptySet<MethodTrait>(), MethodTraits.ofCall("getAt", "(I)I"))
        assertEquals(emptySet<MethodTrait>(), MethodTraits.ofCall("is", "()Z"))
        assertEquals(emptySet<MethodTrait>(), MethodTraits.ofCall("get", "()I"))
        // void return or non-boolean is-prefix
        assertEquals(emptySet<MethodTrait>(), MethodTraits.ofCall("getX", "()V"))
        assertEquals(emptySet<MethodTrait>(), MethodTraits.ofCall("isReady", "()I"))
        // lowercase tail after the prefix
        assertEquals(emptySet<MethodTrait>(), MethodTraits.ofCall("getting", "()I"))
    }

    @Test
    fun `setter shapes and near-misses`() {
        assertEquals(setOf(MethodTrait.SETTER), MethodTraits.ofCall("setX", "(I)V"))
        assertEquals(emptySet<MethodTrait>(), MethodTraits.ofCall("setTag", "(Ljava/lang/String;)Ljava/lang/String;")) // non-void return
        assertEquals(emptySet<MethodTrait>(), MethodTraits.ofCall("setAll", "(II)V")) // two params
        assertEquals(emptySet<MethodTrait>(), MethodTraits.ofCall("set", "(I)V")) // empty tail
    }

    @Test
    fun `declared methods add STATIC, calls never do`() {
        assertEquals(
            setOf(MethodTrait.STATIC, MethodTrait.GETTER),
            MethodTraits.ofDeclared("getConfig", "()Ljava/lang/String;", isStatic = true),
        )
        assertEquals(setOf(MethodTrait.GETTER), MethodTraits.ofDeclared("getConfig", "()Ljava/lang/String;", isStatic = false))
        // a static method CALLED carries no STATIC trait: the dispatch is the edge type
        assertEquals(emptySet<MethodTrait>(), MethodTraits.ofCall("stat", "(I)I"))
        assertEquals(emptySet<MethodTrait>(), MethodTraits.ofCall("<clinit>", "()V"))
    }

    @Test
    fun `kotlin-style accessors qualify too`() {
        assertEquals(setOf(MethodTrait.GETTER), MethodTraits.ofCall("isEmpty", "()Z"))
        assertEquals(emptySet<MethodTrait>(), MethodTraits.ofCall("isEmpty", "()I"))
    }

    @Test
    fun `traits are deterministic`() {
        assertTrue(MethodTraits.ofDeclared("getConfig", "()Ljava/lang/String;", true).toList() ==
            MethodTraits.ofDeclared("getConfig", "()Ljava/lang/String;", true).toList())
    }
}
