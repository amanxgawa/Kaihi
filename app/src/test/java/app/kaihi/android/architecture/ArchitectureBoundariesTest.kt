package app.kaihi.android.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.io.File
import org.junit.Assert.fail
import org.junit.Test

private val CORE_MODULE_ORDER = listOf("security", "storage", "mode", "ssh", "vpn")
private const val APP = "app.kaihi.android"

/**
 * Rules are specified in CONTRIBUTING.md#architecture.
 * Each test enforces one row or bullet from that specification.
 */
class ArchitectureBoundariesTest {

    @Test
    fun `platform depends on nothing else in the app`() {
        val violations = importViolations { file, name ->
            isPlatform(file.packagee?.name.orEmpty()) &&
                (name.isInPackage("$APP.core") || name.isInPackage("$APP.feature") ||
                    isAppRootImport(name))
        }
        assertConforms("platform depends on nothing else in the app", violations)
    }

    @Test
    fun `core modules import only modules earlier in the order`() {
        // Import violations use '<file location> imports <name>'; unknown modules add guidance.
        val violations = mutableListOf<String>()
        projectFiles.forEach { file ->
            val module = coreModuleOf(file.packagee?.name.orEmpty()) ?: return@forEach
            if (module !in CORE_MODULE_ORDER) {
                violations += "${file.describeLocation()} declares unknown core module $module; " +
                    "add it to the order in CONTRIBUTING and the test"
            }
            file.imports.forEach importLoop@{ import ->
                val importedModule = coreModuleOf(import.name) ?: return@importLoop
                if (importedModule !in CORE_MODULE_ORDER) {
                    violations += "${file.describeLocation()} imports ${import.name}; " +
                        "add core module $importedModule to the order in CONTRIBUTING and the test"
                } else if (module in CORE_MODULE_ORDER &&
                    CORE_MODULE_ORDER.indexOf(importedModule) > CORE_MODULE_ORDER.indexOf(module)
                ) {
                    violations += "${file.describeLocation()} imports ${import.name}"
                }
            }
        }
        assertConforms(
            "core modules import only modules earlier in the order",
            violations,
        )
    }

    @Test
    fun `core never imports feature, the app root, or platform beyond dispatchers`() {
        val violations = importViolations { file, name ->
            coreModuleOf(file.packagee?.name.orEmpty()) != null &&
                (name.isInPackage("$APP.feature") || isAppRootImport(name) ||
                    (isPlatform(name) && !name.isInPackage("$APP.platform.di")))
        }
        assertConforms(
            "core never imports feature, the app root, or platform beyond dispatchers",
            violations,
        )
    }

    @Test
    fun `core public API uses no framework or IO code`() {
        val violations = importViolations { file, name ->
            isCorePublic(file.packagee?.name.orEmpty()) &&
                (isFrameworkImport(name) ||
                    listOf(
                        "net.schmizz.", "com.hierynomus.", "org.bouncycastle.",
                        "libv2ray.", "go.", "javax.net.", "java.io.File",
                    ).any(name::startsWith) ||
                    (name.startsWith("java.net.") && name !in setOf(
                        "java.net.URI", "java.net.URLEncoder", "java.net.URLDecoder", "java.net.IDN",
                    ) && !name.substringAfterLast('.').endsWith("Exception")))
        }
        assertConforms(
            "core public API uses no framework or I/O code",
            violations,
        )
    }

    @Test
    fun `core public objects and top-level declarations hold no mutable state`() {
        val violations = mutableListOf<String>()
        projectFiles.filter { isCorePublic(it.packagee?.name.orEmpty()) }.forEach { file ->
            // Konsist supplies fullyQualifiedName only for top-level properties.
            val properties = file.properties(includeNested = false).map { it to it.fullyQualifiedName } +
                file.objects(includeNested = true).flatMap { obj ->
                    obj.properties(includeNested = false).map { it to "${obj.fullyQualifiedName}.${it.name}" }
                }
            properties.filter { (property, _) -> property.hasVarModifier }.forEach { (_, name) ->
                violations += "$name holds mutable state in the public core API"
            }
        }
        assertConforms(
            "core public API exposes only abstractions, data types and pure code",
            violations,
        )
    }

    @Test
    fun `feature domain is pure Kotlin and uses no adapters`() {
        val violations = importViolations { file, name ->
            val (feature, layer) = featureLayerOf(file.packagee?.name.orEmpty())
                ?: return@importViolations false
            layer == "domain" &&
                (isFrameworkImport(name) || name.isInPackage("$APP.platform.ui") ||
                    name.isInPackage("$APP.platform.navigation") ||
                    name.isInPackage("$APP.feature.$feature.impl.ui") ||
                    name.isInPackage("$APP.feature.$feature.impl.data") ||
                    isOtherFeatureImport(name, feature) ||
                    name in setOf(
                        "$APP.core.storage.AppStorage", "$APP.core.storage.StorageDomain",
                        "$APP.core.storage.ExperienceKeys",
                    ))
        }
        assertConforms(
            "feature domain is pure Kotlin and uses no adapters", violations,
        )
    }

    @Test
    fun `feature data does not depend on ui or other features`() {
        val violations = importViolations { file, name ->
            val (feature, layer) = featureLayerOf(file.packagee?.name.orEmpty())
                ?: return@importViolations false
            layer == "data" && (name.isInPackage("$APP.feature.$feature.impl.ui") ||
                isOtherFeatureImport(name, feature))
        }
        assertConforms(
            "feature data does not depend on ui or other features", violations,
        )
    }

    @Test
    fun `feature ui reads core types but never calls core behaviour`() {
        val violations = importViolations { file, name ->
            val (feature, layer) = featureLayerOf(file.packagee?.name.orEmpty())
                ?: return@importViolations false
            layer == "ui" &&
                (name.isInPackage("$APP.feature.$feature.impl.data") ||
                    (name.startsWith("$APP.core.") && resolveCoreKind(name) != CoreKind.ALLOWED))
        }
        assertConforms(
            "feature ui reads core types but never calls core behaviour", violations,
        )
    }

    @Test
    fun `features reach other features only through root keys in navigation`() {
        val violations = importViolations { file, name ->
            val pkg = file.packagee?.name.orEmpty()
            val (feature, _) = featureLayerOf(pkg) ?: return@importViolations false
            if (!isOtherFeatureImport(name, feature)) return@importViolations false
            val declaration = name.removePrefix("$APP.feature.").substringAfter('.')
            !pkg.isInPackage("$APP.feature.$feature.impl.ui.navigation") ||
                declaration.contains('.') || !declaration.endsWith("Key")
        }
        assertConforms(
            "features reach other features only through root keys in navigation",
            violations,
        )
    }

    @Test
    fun `only the app root imports the app root`() {
        val violations = importViolations { file, name ->
            !isAppRoot(file.packagee?.name.orEmpty()) && isAppRootImport(name)
        }
        assertConforms("only the app root imports the app root", violations)
    }

    @Test
    fun `stored flag keys are used only inside core`() {
        val violations = importViolations { file, name ->
            val pkg = file.packagee?.name.orEmpty()
            name == "$APP.core.storage.ExperienceKeys" &&
                !pkg.isInPackage("$APP.core")
        }
        assertConforms(
            "stored flag keys are used only inside core",
            violations,
        )
    }

    @Test
    fun `feature root packages expose only navigation keys`() {
        // File violations use '<file location> is not a root navigation key'.
        val violations = rootFeatureFiles().filterNot { it.fileName().endsWith("Key.kt") }
            .map { "${it.describeLocation()} is not a root navigation key" }
        assertConforms(
            "feature root packages expose only navigation keys", violations,
        )
    }

    @Test
    fun `every feature navigation key has a registered Destination`() {
        // File violations use '<file location> has no Destination<Key> under its feature impl'.
        val rootKeyFiles = rootFeatureFiles().filter { it.fileName().endsWith("Key.kt") }
        val violations = rootKeyFiles.filterNot { keyFile ->
            val featurePkg = keyFile.packagee?.name ?: return@filterNot false
            val keyName = keyFile.fileName().removeSuffix(".kt")
            projectClasses.any { clazz ->
                clazz.packagee?.name.orEmpty().isInPackage("$featurePkg.impl") &&
                    clazz.text.contains("Destination<$keyName>")
            }
        }.map { "${it.describeLocation()} has no Destination<${it.fileName().removeSuffix(".kt")}> under its feature impl" }
        assertConforms(
            "every feature navigation key has a registered Destination", violations,
        )
    }

    @Test
    fun `feature impl and core internal are never reached from outside their own module`() {
        val violations = importViolations { file, name ->
            val pkg = file.packagee?.name.orEmpty()
            val coreModule = coreModuleOf(name)
            val feature = name.takeIf { it.startsWith("$APP.feature.") }
                ?.removePrefix("$APP.feature.")?.substringBefore('.')
            (coreModule != null && name.isInPackage("$APP.core.$coreModule.internal") &&
                !pkg.isInPackage("$APP.core.$coreModule.internal") &&
                !pkg.isInPackage("$APP.core.$coreModule.di")) ||
                (feature != null && name.isInPackage("$APP.feature.$feature.impl") &&
                    !pkg.isInPackage("$APP.feature.$feature.impl"))
        }
        assertConforms(
            "feature impl and core internal are never reached from outside their own module",
            violations,
        )
    }

    // All import rules use '<file package>.<file name> imports <fully qualified import name>'.
    private fun importViolations(
        isViolation: (KoFileDeclaration, String) -> Boolean,
    ): List<String> = projectFiles.flatMap { file ->
        file.imports.filter { isViolation(file, it.name) }
            .map { "${file.describeLocation()} imports ${it.name}" }
    }

    private fun assertConforms(rule: String, violations: List<String>) {
        if (violations.isEmpty()) return

        fail(buildString {
            appendLine(rule)
            appendLine(violations.sorted().joinToString("\n") { " - $it" })
        })
    }

    private fun featureLayerOf(pkg: String): Pair<String, String>? {
        if (!pkg.startsWith("$APP.feature.")) return null
        val parts = pkg.removePrefix("$APP.feature.").split('.')
        val feature = parts.first().takeIf { it.isNotEmpty() } ?: return null
        val layer = when {
            parts.size == 1 -> "root"
            parts.getOrNull(1) == "impl" -> parts.getOrNull(2) ?: return null
            else -> return null
        }
        return (feature to layer).takeIf { layer in setOf("domain", "data", "ui", "di", "root") }
    }

    private fun isAppRoot(pkg: String): Boolean = pkg == APP

    private fun isPlatform(pkg: String): Boolean = pkg.isInPackage("$APP.platform")

    private fun isAppRootImport(name: String): Boolean = name.startsWith("$APP.") &&
        name.removePrefix("$APP.").substringBefore('.') !in setOf("core", "feature", "platform") &&
        !name.isInPackage("$APP.R") && name != "$APP.BuildConfig"

    private fun isFrameworkImport(name: String): Boolean = name.startsWith("android.") ||
        (name.startsWith("androidx.") && !name.startsWith("androidx.annotation."))

    private fun isOtherFeatureImport(name: String, ownFeature: String): Boolean =
        name.startsWith("$APP.feature.") &&
            name.removePrefix("$APP.feature.").substringBefore('.') != ownFeature

    private fun resolveCoreKind(name: String): CoreKind? {
        var prefix = name
        while (prefix.startsWith("$APP.core.")) {
            coreDeclarations[prefix]?.let { return it }
            prefix = prefix.substringBeforeLast('.')
        }
        return null
    }

    private fun rootFeatureFiles(): List<KoFileDeclaration> = projectFiles.filter { file ->
        featureLayerOf(file.packagee?.name.orEmpty())?.second == "root"
    }

    private fun KoFileDeclaration.fileName(): String = File(path).name

    private fun KoFileDeclaration.describeLocation(): String = "${packagee?.name}.${fileName()}"

    private enum class CoreKind { ALLOWED, BANNED }

    private companion object {
        fun coreModuleOf(pkg: String): String? =
            pkg.takeIf { it.startsWith("$APP.core.") }
                ?.removePrefix("$APP.core.")?.substringBefore('.')?.takeIf { it.isNotEmpty() }

        fun isCorePublic(pkg: String): Boolean {
            val module = coreModuleOf(pkg) ?: return false
            return !pkg.isInPackage("$APP.core.$module.internal") &&
                !pkg.isInPackage("$APP.core.$module.di")
        }

        fun String.isInPackage(pkg: String): Boolean = this == pkg || startsWith("$pkg.")

        fun KoClassDeclaration.isUiDataType(): Boolean =
            hasDataModifier || hasEnumModifier || hasValueModifier || hasAnnotationModifier ||
                hasSealedModifier || name.endsWith("Exception") || name.endsWith("Error")

        val projectFiles: List<KoFileDeclaration> =
            Konsist.scopeFromProject().files.filter { it.path.contains("/main/java/") }

        val projectClasses: List<KoClassDeclaration> = projectFiles.flatMap { it.classes() }

        // Built once for the suite; nested declarations override their enclosing type on lookup.
        val coreDeclarations: Map<String, CoreKind> by lazy {
            buildMap {
                projectFiles.filter { isCorePublic(it.packagee?.name.orEmpty()) }
                    .forEach { file ->
                        file.classes(includeNested = true).forEach { declaration ->
                            declaration.fullyQualifiedName?.let { name ->
                                put(name, if (declaration.isUiDataType()) {
                                    CoreKind.ALLOWED
                                } else {
                                    CoreKind.BANNED
                                })
                            }
                        }
                        file.interfaces(includeNested = true).forEach { declaration ->
                            declaration.fullyQualifiedName?.let { name ->
                                put(name, if (declaration.hasSealedModifier) CoreKind.ALLOWED else CoreKind.BANNED)
                            }
                        }
                        file.objects(includeNested = true).forEach { declaration ->
                            declaration.fullyQualifiedName?.let { name ->
                                put(name, if (declaration.hasDataModifier) CoreKind.ALLOWED else CoreKind.BANNED)
                            }
                        }
                        file.functions(includeNested = false, includeLocal = false).forEach { declaration ->
                            declaration.fullyQualifiedName?.let { put(it, CoreKind.ALLOWED) }
                        }
                        file.properties(includeNested = false).forEach { declaration ->
                            declaration.fullyQualifiedName?.let { put(it, CoreKind.ALLOWED) }
                        }
                    }
            }
        }
    }
}
