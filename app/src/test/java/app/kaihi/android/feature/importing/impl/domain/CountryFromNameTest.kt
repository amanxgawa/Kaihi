package app.kaihi.android.feature.importing.impl.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountryFromNameTest {
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
}
