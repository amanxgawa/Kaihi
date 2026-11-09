package app.kaihi.android.core.vpn

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ImportInputTest {

    @Test
    fun `classifies absolute HTTPS and HTTP subscription URLs`() {
        val remote = classifyImportInput("  https://panel.example/sub/token  ")
        val insecure = classifyImportInput("http://panel.example/sub/token")

        assertEquals(ImportInput.RemoteSubscription("https://panel.example/sub/token"), remote)
        assertEquals(ImportInput.InsecureUrl("http://panel.example/sub/token"), insecure)
    }

    @Test
    fun `does not treat URLs with credentials or whitespace as remote subscriptions`() {
        assertEquals(
            ImportInput.Links(listOf("https://user:pass@panel.example/sub/token")),
            classifyImportInput("https://user:pass@panel.example/sub/token"),
        )
        assertEquals(
            ImportInput.Links(listOf("https://panel.example/a b")),
            classifyImportInput("https://panel.example/a b"),
        )
    }

    @Test
    fun `classifies pasted plain and base64-encoded link lists`() {
        val link = "vless://uuid@node.example:443?security=tls#Node"
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(link.toByteArray())

        assertEquals(ImportInput.Links(listOf(link)), classifyImportInput("  $link  "))
        assertEquals(ImportInput.Links(listOf(link)), classifyImportInput(encoded))
    }

    @Test
    fun `returns unrecognized for empty and metadata-only input`() {
        assertEquals(ImportInput.Unrecognized, classifyImportInput(" \n\t "))
        assertEquals(ImportInput.Unrecognized, classifyImportInput("#profile-title: Panel"))
    }

    @Test
    fun `redacts pasted link and subscription URL contents from diagnostic strings`() {
        val fakeUuid = "f00dbabe-1111-2222-3333-deadbeefcafe"
        val linkInput = classifyImportInput("vless://$fakeUuid@node.example:443")
        val remoteInput = classifyImportInput("https://panel.example/sub/SECRET-TOKEN")

        assertFalse(linkInput.toString().contains(fakeUuid))
        assertFalse(remoteInput.toString().contains("SECRET-TOKEN"))
    }
}
