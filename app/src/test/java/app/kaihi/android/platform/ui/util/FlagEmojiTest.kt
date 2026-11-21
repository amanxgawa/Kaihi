package app.kaihi.android.platform.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlagEmojiTest {
    @Test
    fun `finds first pair of regional indicators`() {
        assertEquals("NL", countryCodeFromFlag("\uD83C\uDDF3\uD83C\uDDF1 NL-3"))
        assertEquals("DE", countryCodeFromFlag("Frankfurt \uD83C\uDDE9\uD83C\uDDEA"))
    }

    @Test
    fun `returns null without two consecutive regional indicators`() {
        assertNull(countryCodeFromFlag("plain"))
        assertNull(countryCodeFromFlag("\uD83C\uDDF3 x"))
    }

    @Test
    fun `removes only a leading flag when a name remains`() {
        assertEquals("Amsterdam", "\uD83C\uDDF3\uD83C\uDDF1 Amsterdam".withoutLeadingFlag())
        assertEquals("Amsterdam", "\uD83C\uDDF3\uD83C\uDDF1Amsterdam".withoutLeadingFlag())
        assertEquals("NL-3", "  \uD83C\uDDF3\uD83C\uDDF1   NL-3".withoutLeadingFlag())
        assertEquals("Frankfurt \uD83C\uDDE9\uD83C\uDDEA", "Frankfurt \uD83C\uDDE9\uD83C\uDDEA".withoutLeadingFlag())
        assertEquals("\uD83C\uDDF3\uD83C\uDDF1", "\uD83C\uDDF3\uD83C\uDDF1".withoutLeadingFlag())
        assertEquals("\uD83C\uDDE9\uD83C\uDDEA Mixed", "\uD83C\uDDF3\uD83C\uDDF1 \uD83C\uDDE9\uD83C\uDDEA Mixed".withoutLeadingFlag())
        assertEquals("\uD83C\uDDF3 x", "\uD83C\uDDF3 x".withoutLeadingFlag())
    }

    @Test
    fun `stored country code takes precedence over a flag in the name`() {
        assertEquals("\uD83C\uDDE9\uD83C\uDDEA", flagFor("DE", "\uD83C\uDDF3\uD83C\uDDF1 x"))
        assertEquals("\uD83C\uDDF3\uD83C\uDDF1", flagFor(null, "\uD83C\uDDF3\uD83C\uDDF1 x"))
        assertNull(flagFor(null, "plain"))
    }
}
