package app.kaihi.android.feature.importing.impl.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UntrustedTextTest {
    @Test
    fun `removes bidi display controls`() {
        assertEquals("abcdef", cleanUntrustedText("abc\u202Edef", 32))
        assertEquals("x", cleanUntrustedText("\u2066x\u2069", 32))
        assertEquals("abc", cleanUntrustedText("a\u200Fb\u061Cc", 32))
    }

    @Test
    fun `replaces controls and optionally keeps newlines`() {
        assertEquals("line1 line2", cleanUntrustedText("line1\nline2", 32))
        assertEquals("line1\nline2", cleanUntrustedText("line1\nline2", 32, keepNewlines = true))
        assertEquals("a b", cleanUntrustedText("a\u0000b", 32))
    }

    @Test
    fun `keeps joiners and truncates by code point`() {
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69"
        assertEquals(family, cleanUntrustedText(family, 8))

        val faces = "\uD83D\uDE00".repeat(5)
        assertEquals("\uD83D\uDE00".repeat(3) + "…", cleanUntrustedText(faces, 3))
    }
}
