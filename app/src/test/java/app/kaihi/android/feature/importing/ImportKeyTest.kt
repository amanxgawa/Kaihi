package app.kaihi.android.feature.importing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportKeyTest {
    @Test
    fun `text is redacted from toString`() {
        val rendered = ImportKey(text = "vless://SECRET@host:443").toString()

        assertFalse(rendered.contains("SECRET"))
    }

    @Test
    fun `null text is shown explicitly`() {
        assertTrue(ImportKey().toString().startsWith("ImportKey(text=null, requestId="))
    }

    @Test
    fun `same text has different toString values`() {
        val first = ImportKey(text = "vless://user@host:443")
        val second = ImportKey(text = "vless://user@host:443")

        assertNotEquals(first.toString(), second.toString())
    }
}
