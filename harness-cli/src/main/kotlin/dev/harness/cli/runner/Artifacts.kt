package dev.harness.cli.runner

import dev.harness.cli.contract.Verdict
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

val json = Json { prettyPrint = true; encodeDefaults = true }
private val jsonLine = Json { encodeDefaults = true }

@Serializable
data class RunManifest(
    val schemaVersion: Int = 1,
    val runId: String,
    val createdAt: String,
    val sourceCommit: String,
    val sourceTreeSha256: String,
    val contractSha256: String,
    val fixtureSha256: String,
    val baselineSha256: String? = null,
    val mode: String = "engineering",
    val scope: String = "L0 headless; UI drivers have not run",
    val overall: Verdict,
    val gates: List<GateResult>,
)

@Serializable data class MockEvidence(val submitCalls: Int, val lookupCalls: Int, val approvedCount: Int, val acceptedSubmissions: Int)

fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

fun git(root: Path, vararg args: String): String {
    val process = ProcessBuilder(listOf("git", "-C", root.toString()) + args).redirectError(ProcessBuilder.Redirect.INHERIT).start()
    val output = process.inputStream.readBytes()
    check(process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0) { "Git metadata could not be read" }
    return output.toString(Charsets.UTF_8)
}

/** Includes tracked changes, deletions and non-ignored new files; HEAD alone is insufficient. */
fun sourceTreeHash(root: Path): String {
    val names = git(root, "ls-files", "--cached", "--others", "--exclude-standard", "-z")
        .split('\u0000').filter { it.isNotEmpty() }.distinct().sorted()
    val digest = MessageDigest.getInstance("SHA-256")
    for (name in names) {
        val path = root.resolve(name)
        digest.update(name.toByteArray()); digest.update(0.toByte())
        if (Files.isSymbolicLink(path)) digest.update(("symlink:" + Files.readSymbolicLink(path)).toByteArray())
        else if (path.isRegularFile()) digest.update(sha256(path.readBytes()).toByteArray())
        else digest.update("deleted".toByteArray())
        digest.update(0.toByte())
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun writeRun(root: Path, contract: ByteArray, fixture: ByteArray, result: ScenarioResult, sourceHash: String): Path {
    check(sourceHash == sourceTreeHash(root)) { "Source changed during the run; rerun against stable inputs" }
    val runId = UUID.randomUUID().toString()
    val path = root.resolve("artifacts").resolve(runId).createDirectories()
    val gates = Gates.complete(listOf(result.gate))
    val manifest = RunManifest(runId = runId, createdAt = Instant.now().toString(), sourceCommit = git(root, "rev-parse", "HEAD").trim(),
        sourceTreeSha256 = sourceHash, contractSha256 = sha256(contract), fixtureSha256 = sha256(fixture),
        overall = Gates.overall(gates), gates = gates)
    path.resolve("contract.json").writeBytes(contract)
    path.resolve("fixture.json").writeBytes(fixture)
    path.resolve("state.json").writeText(json.encodeToString(result.state))
    path.resolve("events.jsonl").writeText(result.events.joinToString("\n", postfix = "\n") { jsonLine.encodeToString(it) })
    path.resolve("mock-server.json").writeText(json.encodeToString(MockEvidence(result.submitCalls, result.lookupCalls, result.approvedCount, result.acceptedSubmissions)))
    path.resolve("patch.diff").writeText(git(root, "diff", "HEAD", "--", "."))
    ZipOutputStream(Files.newOutputStream(path.resolve("source.zip"))).use { zip ->
        val names = git(root, "ls-files", "--cached", "--others", "--exclude-standard", "-z")
            .split('\u0000').filter { it.isNotEmpty() }.distinct().sorted()
        for (name in names) {
            val file = root.resolve(name)
            // Never follow source symlinks to files outside the repository.
            check(!Files.isSymbolicLink(file)) { "Source symlinks need an explicit snapshot adapter: $name" }
            if (file.isRegularFile()) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(file.readBytes())
                zip.closeEntry()
            }
        }
    }
    path.resolve("report.md").writeText(buildString {
        appendLine("# Run $runId")
        appendLine("\nOverall: **${manifest.overall}**. L0: **${result.gate.verdict}**.")
        appendLine("\n${manifest.scope}. No visual baseline has been approved.")
        appendLine("\n| Gate | Verdict | Detail |\n|---|---|---|")
        gates.forEach { appendLine("| ${it.id} | ${it.verdict} | ${it.detail.replace('|', '/')} |") }
        appendLine("\nSource files, including uncommitted additions, are in source.zip. Restore executable permissions on gradlew and harness when extracting.")
        appendLine("\nReproduce from the recorded source tree:")
        appendLine("\n```sh\n./harness scenario --fixture artifacts/$runId/fixture.json --contract artifacts/$runId/contract.json\n```")
        appendLine("\nFixture and UI observations are synthetic. A passing L0 check does not prove app usability.")
    })
    check(sourceHash == sourceTreeHash(root)) { "Source changed while writing evidence; incomplete run has no manifest" }
    // The manifest is the completion marker; partial evidence must never look complete.
    path.resolve("manifest.json").writeText(json.encodeToString(manifest))
    return path
}
