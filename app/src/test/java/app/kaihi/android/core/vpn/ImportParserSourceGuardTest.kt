package app.kaihi.android.core.vpn

import com.lemonappdev.konsist.api.Konsist
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ImportParserSourceGuardTest {

    @Test
    fun `import parser sources do not use DNS-capable address lookups`() {
        val parserFiles = Konsist.scopeFromProject().files.filter { file ->
            file.path.contains("/main/java/") && File(file.path).name in IMPORT_PARSER_FILES
        }

        assertEquals(IMPORT_PARSER_FILES, parserFiles.map { File(it.path).name }.toSet())
        parserFiles.forEach { file ->
            val source = File(file.path).readText()
            assertFalse("${File(file.path).name} references InetAddress", source.contains("InetAddress"))
            assertFalse("${File(file.path).name} references getByName", source.contains("getByName"))
        }
    }

    private companion object {
        val IMPORT_PARSER_FILES = setOf(
            "ImportInput.kt",
            "ImportParsingUtils.kt",
            "SubscriptionMetadataParser.kt",
            "VlessLinkParser.kt",
        )
    }
}
