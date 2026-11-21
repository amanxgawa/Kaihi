package app.kaihi.android.feature.wizard

import com.lemonappdev.konsist.api.Konsist
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerFootprintSourceGuardTest {

    @Test
    fun `server footprint sources do not contain Kaihi names`() {
        val sourceFiles = Konsist.scopeFromProject().files.filter { file ->
            val path = file.path.replace('\\', '/')
            path.contains("/main/java/app/kaihi/android/feature/wizard/impl/domain/command/") ||
                path.contains("/main/java/app/kaihi/android/feature/wizard/impl/domain/deployment/") ||
                path.endsWith("/main/java/app/kaihi/android/feature/wizard/impl/domain/DeployXrayServerUseCase.kt") ||
                path.endsWith("/main/java/app/kaihi/android/core/vpn/configfactory/XrayServerConfigFactory.kt")
        }

        assertFalse("No server footprint production sources were found", sourceFiles.isEmpty())
        val foundNames = sourceFiles.map { File(it.path).name }.toSet()
        EXPECTED_FILES.forEach { expected ->
            assertTrue("Missing server footprint source: $expected", expected in foundNames)
        }

        sourceFiles.forEach { file ->
            val source = File(file.path).readLines()
            source.forEachIndexed { index, line ->
                if (!line.startsWith("package ") && !line.startsWith("import ")) {
                    assertFalse(
                        "${File(file.path).name}:${index + 1} contains Kaihi in server footprint source",
                        line.contains("kaihi", ignoreCase = true),
                    )
                }
            }
        }
    }

    private companion object {
        val EXPECTED_FILES = setOf(
            "CreateXrayServiceUserCommand.kt",
            "DetectIpv6SupportCommand.kt",
            "DetectPackageManagerCommand.kt",
            "DetectServerArchCommand.kt",
            "DetectServerCountryCommand.kt",
            "GenerateXrayServerSecretsCommand.kt",
            "InstallXrayRuntimeCommand.kt",
            "ProtectServerCommand.kt",
            "ScanSniCommand.kt",
            "SecureXrayConfigCommand.kt",
            "StartXrayServiceCommand.kt",
            "VerifySniCandidateCommand.kt",
            "XrayOpenRcServiceFactory.kt",
            "XraySystemdServiceFactory.kt",
            "DeployXrayServerUseCase.kt",
            "XrayServerConfigFactory.kt",
        )
    }
}
