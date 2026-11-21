package app.kaihi.android.feature.importing

import app.kaihi.android.feature.importing.impl.ui.mvi.ImportContract
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportKeyTest {
    @Test
    fun `null text is shown explicitly`() {
        assertTrue(ImportKey().toString().startsWith("ImportKey(text=null, offerServerSetup=false, requestId="))
    }

    @Test
    fun `same text has different toString values`() {
        val first = ImportKey(text = "vless://user@host:443")
        val second = ImportKey(text = "vless://user@host:443")

        assertNotEquals(first.toString(), second.toString())
    }

    @Test
    fun `redacts subscription token from key and pasted intent`() {
        val token = "private-token"
        val link = "https://provider.example/$token"

        val keyText = ImportKey(text = link, offerServerSetup = true).toString()
        assertFalse(keyText.contains(token))
        assertTrue(keyText.contains("offerServerSetup=true"))

        val pastedText = ImportContract.Intent.Pasted(link).toString()
        assertFalse(pastedText.contains(token))
    }
}
