package app.kaihi.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingImportTextTest {
    @Test
    fun `view intent accepts vless links`() {
        val link = "vless://uuid@host:443?type=tcp#Anton"

        assertEquals(link, incomingImportText(VIEW, link, null, null))
    }

    @Test
    fun `view intent compares vless scheme case insensitively`() {
        val link = "VLESS://uuid@host:443"

        assertEquals(link, incomingImportText(VIEW, link, null, null))
    }

    @Test
    fun `view intent rejects https and missing data`() {
        assertNull(incomingImportText(VIEW, "https://example.com/sub", null, null))
        assertNull(incomingImportText(VIEW, null, null, null))
    }

    @Test
    fun `text share trims links and accepts subscription urls`() {
        assertEquals("vless://a@b:1", incomingImportText(SEND, null, TEXT_PLAIN, "  vless://a@b:1  "))
        assertEquals(
            "https://sub.example/s/abc",
            incomingImportText(SEND, null, TEXT_PLAIN, "https://sub.example/s/abc"),
        )
    }

    @Test
    fun `text share rejects other mime types and blank text`() {
        assertNull(incomingImportText(SEND, null, "image/png", "some text"))
        assertNull(incomingImportText(SEND, null, TEXT_PLAIN, "   "))
    }

    @Test
    fun `text share enforces maximum length`() {
        assertNull(incomingImportText(SEND, null, TEXT_PLAIN, "a".repeat(65_537)))
        assertEquals("a".repeat(65_536), incomingImportText(SEND, null, TEXT_PLAIN, "a".repeat(65_536)))
    }

    @Test
    fun `other actions are rejected`() {
        assertNull(incomingImportText("android.intent.action.MAIN", null, null, null))
    }

    private companion object {
        const val VIEW = "android.intent.action.VIEW"
        const val SEND = "android.intent.action.SEND"
        const val TEXT_PLAIN = "text/plain"
    }
}
