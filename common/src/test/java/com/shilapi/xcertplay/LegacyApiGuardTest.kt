package com.shilapi.xcertplay

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the calls that raised `NoSuchMethodError`/`NoClassDefFoundError` on Android 4.2.
 *
 * `minSdk` is 17, but `desugar_jdk_libs` only backports the java.time/stream/nio/function APIs it
 * ships. Android framework and remaining JDK methods are not backported at all, so referencing one
 * above the platform level fails at execution time with a `LinkageError` that no
 * `catch (error: Exception)` around it will contain. On 4.2 that turned the AirPlay video SETUP
 * into a crash whenever the iPhone's 64-bit seed had its high bit set.
 *
 * Each entry names a call that is missing on API 17. Every one of them replaced something that
 * works there; do not put the original back, and keep each behind a real version check if the call
 * genuinely cannot be avoided.
 */
class LegacyApiGuardTest {
    private val forbidden = mapOf(
        "Long.toUnsignedString" to "API 26; render unsigned decimals with Long.unsignedDecimal",
        "Integer.toUnsignedString" to "API 26; render unsigned decimals with Int.unsignedDecimal",
        "MotionEvent.actionToString" to "API 19; use touchActionName",
        "Math.floorDiv" to "API 24; use floorDivBySecond",
        "Math.floorMod" to "API 24; use floorModBySecond",
        ".addSuppressed(" to "API 19; use Throwable.suppressSecondary",
        "getSuppressed()" to "API 19; no suppressed list exists on API 17",
        "Objects.requireNonNull" to "API 19; Kotlin's requireNotNull is compiled in",
        "InetAddress.getLoopbackAddress" to "API 19; use Inet4Address.LOOPBACK",
        "HandlerThread.quitSafely" to "API 18; call quit() below API 18",
        "ReflectiveOperationException" to "API 19; a catch clause naming it fails class verification, use isReflectiveFailure",
        ".removeIf" to "API 24; iterate the collection instead",
        ".computeIfAbsent" to "API 24; use get + putIfAbsent on ConcurrentHashMap",
        "ThreadLocal.withInitial" to "API 26; override initialValue() in a ThreadLocal subclass",
        "AutoCloseable" to "API 19; implement java.io.Closeable, which exists since API 1",
        ": Closeable>" to "API 19; Socket/ServerSocket/DatagramSocket only implement Closeable from API 19, close the concrete type",
        "as Closeable" to "API 19; Socket/ServerSocket/DatagramSocket are not Closeable on 17/18, call the concrete close()",
        "Socket().use" to "API 19; use inserts an implicit Closeable cast, use try/finally",
        "lock().use" to "API 19; FileLock is not AutoCloseable on 17/18, release() in a finally block",
    )

    @Test fun noMainSourceCallsAnApiThatIsMissingOnAndroid42() {
        val offenders = mutableListOf<String>()
        val blockComment = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        for (file in mainSourceFiles()) {
            val relative = file.relativeTo(repositoryRoot()).path
            // Blank out block comments but keep the line numbers, so a report points at the
            // real line and prose about a banned call is not mistaken for one.
            val text = file.readText().replace(blockComment) { match ->
                match.value.replace(Regex("[^\\n]"), " ")
            }
            text.lines().forEachIndexed { index, raw ->
                val code = raw.substringBefore("//")
                if (code.isBlank()) return@forEachIndexed
                for ((call, reason) in forbidden) {
                    if (call in code) offenders += "$relative:${index + 1}  $call  ($reason)  ${code.trim()}"
                }
            }
        }
        assertEquals(
            "Calls missing on Android 4.2 are back in the main source set:\n" + offenders.joinToString("\n"),
            emptyList<String>(),
            offenders,
        )
    }

    @Test fun theGuardIgnoresCommentsAndItsOwnDocumentation() {
        // A guard that trips on the prose describing the ban reports every offender forever.
        assertEquals(
            emptyList<String>(),
            reportedIn(
                """
                package com.example
                /**
                 * `Math.floorMod` is API 24, so use floorModBySecond instead.
                 * Long.toUnsignedString and Integer.toUnsignedString are API 26.
                 */
                // Math.floorDiv in a line comment
                fun fine(a: Long) = a
                """.trimIndent(),
            ),
        )
    }

    @Test fun theGuardStillReportsRealCalls() {
        assertEquals(
            listOf("Math.floorDiv (API 24; use floorDivBySecond)  val x = Math.floorDiv(a, b)"),
            reportedIn(
                """
                package com.example
                fun f(a: Long, b: Long) = run {
                    val x = Math.floorDiv(a, b)
                    x
                }
                """.trimIndent(),
            ),
        )
    }

    private fun reportedIn(source: String): List<String> {
        // Same rule as the real scan: line comments go, and a line inside a block comment is
        // documentation rather than a call.
        val inBlock = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        val text = source.replace(inBlock) { match -> match.value.replace(Regex("[^\\n]"), " ") }
        val offenders = mutableListOf<String>()
        text.lines().forEach { raw ->
            val code = raw.substringBefore("//")
            if (code.isBlank()) return@forEach
            for ((call, reason) in forbidden) {
                if (call in code) offenders += "$call ($reason)  ${code.trim()}"
            }
        }
        return offenders
    }

    @Test fun theGuardActuallySeesTheSourcesItIsGuarding() {
        // A guard that silently scans nothing would pass forever.
        val files = mainSourceFiles()
        assertTrue("expected the three minSdk-17 modules, found ${files.size} files", files.size > 300)
        assertTrue(files.any { it.path.endsWith("CarPlayHostActivity.kt") })
        assertTrue(files.any { it.path.endsWith("CarPlayMediaEngine.kt") })
        assertTrue(files.any { it.path.endsWith("AndroidMediaSink.kt") })
    }

    private fun repositoryRoot(): File {
        var directory: File? = File(System.getProperty("user.dir")).absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").isFile) {
            directory = directory.parentFile
        }
        return directory ?: error("Cannot locate the repository root from ${System.getProperty("user.dir")}")
    }

    private fun mainSourceFiles(): List<File> {
        val root = repositoryRoot()
        return listOf("shared", "common", "mobile")
            .flatMap { module ->
                File(root, "$module/src/main").walkTopDown()
                    .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                    .toList()
            }
    }
}