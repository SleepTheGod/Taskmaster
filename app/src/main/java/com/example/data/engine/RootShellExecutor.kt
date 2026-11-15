package com.example.data.engine

import com.example.model.RootProcessInfo
import com.example.model.RootStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class ShellCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false
) {
    val isSuccess: Boolean get() = !timedOut && exitCode == 0
}

class RootShellExecutor {

    private val packageRegex = Regex("^[a-zA-Z0-9_.]+$")

    private val candidateSuPaths = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/vendor/bin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/magisk/.core/bin/su"
    )

    fun findSuBinary(): String? {
        candidateSuPaths.firstOrNull { path ->
            runCatching { File(path).exists() && File(path).canExecute() }.getOrDefault(false)
        }?.let { return it }

        val pathEnv = System.getenv("PATH").orEmpty()
        for (dir in pathEnv.split(":")) {
            if (dir.isBlank()) continue
            val candidate = File(dir, "su")
            val exists = runCatching { candidate.exists() && candidate.canExecute() }.getOrDefault(false)
            if (exists) return candidate.absolutePath
        }
        return null
    }

    suspend fun probeRootStatus(): RootStatus = withContext(Dispatchers.IO) {
        val suPath = findSuBinary()
        val idResult = executeRootCommand("id", timeoutMs = 3000L)
        val granted = idResult.isSuccess && idResult.stdout.contains("uid=0")

        if (!granted) {
            return@withContext RootStatus(
                isRootBinaryPresent = suPath != null,
                isRootGranted = false,
                suPath = suPath,
                suVersion = null,
                selinuxState = readSelinuxNonRoot(),
                uidOutput = if (idResult.timedOut) {
                    "Timed out awaiting root manager"
                } else {
                    idResult.stderr.ifBlank { idResult.stdout.ifBlank { "No root shell available" } }
                },
                checkedAtMillis = System.currentTimeMillis()
            )
        }

        val metaResult = executeRootCommand(
            "su -v 2>/dev/null || echo 'su'; echo '---'; getenforce 2>/dev/null || echo 'Unknown'",
            timeoutMs = 2500L
        )
        val sections = metaResult.stdout.split("---").map { it.trim() }
        val suVer = sections.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "Root Shell"
        val selinux = sections.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "Unknown"

        RootStatus(
            isRootBinaryPresent = true,
            isRootGranted = true,
            suPath = suPath ?: "su",
            suVersion = suVer,
            selinuxState = selinux,
            uidOutput = idResult.stdout.trim(),
            checkedAtMillis = System.currentTimeMillis()
        )
    }

    private fun readSelinuxNonRoot(): String {
        return runCatching {
            val process = ProcessBuilder("getenforce").redirectErrorStream(true).start()
            val out = process.inputStream.bufferedReader().readText().trim()
            process.waitFor(1000L, TimeUnit.MILLISECONDS)
            out.ifBlank { "Restricted" }
        }.getOrDefault("Restricted")
    }

    suspend fun executeRootCommand(command: String, timeoutMs: Long = 5000L): ShellCommandResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val process = ProcessBuilder("su", "-c", command).start()
                val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    return@runCatching ShellCommandResult(
                        exitCode = -1,
                        stdout = "",
                        stderr = "Command timed out after ${timeoutMs}ms",
                        timedOut = true
                    )
                }
                val stdout = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
                val stderr = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
                ShellCommandResult(
                    exitCode = process.exitValue(),
                    stdout = stdout.trim(),
                    stderr = stderr.trim(),
                    timedOut = false
                )
            }.getOrElse { error ->
                ShellCommandResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = error.message ?: "Unable to invoke su",
                    timedOut = false
                )
            }
        }

    suspend fun forceStopPackages(packages: List<String>): Map<String, Boolean> =
        withContext(Dispatchers.IO) {
            val validPackages = packages.filter { packageRegex.matches(it) }.distinct()
            if (validPackages.isEmpty()) return@withContext emptyMap()

            val results = mutableMapOf<String, Boolean>()
            runCatching {
                val process = ProcessBuilder("su").start()
                process.outputStream.bufferedWriter().use { writer ->
                    for (pkg in validPackages) {
                        writer.write("am force-stop $pkg && echo OK:$pkg || echo ERR:$pkg\n")
                    }
                    writer.write("exit\n")
                    writer.flush()
                }
                val finished = process.waitFor(8000L, TimeUnit.MILLISECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    validPackages.forEach { results[it] = false }
                    return@runCatching
                }
                val output = process.inputStream.bufferedReader().readLines()
                for (line in output) {
                    when {
                        line.startsWith("OK:") -> results[line.removePrefix("OK:").trim()] = true
                        line.startsWith("ERR:") -> results[line.removePrefix("ERR:").trim()] = false
                    }
                }
                for (pkg in validPackages) {
                    if (pkg !in results) {
                        results[pkg] = process.exitValue() == 0
                    }
                }
            }.onFailure {
                validPackages.forEach { results[it] = false }
            }
            results
        }

    suspend fun killPid(pid: Int): Boolean = withContext(Dispatchers.IO) {
        if (pid <= 1) return@withContext false
        val res = executeRootCommand("kill -9 $pid", timeoutMs = 3000L)
        res.isSuccess
    }

    suspend fun queryRunningProcesses(): List<RootProcessInfo> = withContext(Dispatchers.IO) {
        // Modern toybox `ps -A -o PID,RSS,USER,NAME`; fallback to generic `ps` for older Android 7 devices
        var result = executeRootCommand("ps -A -o PID,RSS,USER,NAME 2>/dev/null", timeoutMs = 4000L)
        if (!result.isSuccess || result.stdout.isBlank()) {
            result = executeRootCommand("ps", timeoutMs = 4000L)
            if (!result.isSuccess || result.stdout.isBlank()) return@withContext emptyList()
            return@withContext parseLegacyPs(result.stdout)
        }
        parseStructuredPs(result.stdout)
    }

    private fun parseStructuredPs(stdout: String): List<RootProcessInfo> {
        val list = mutableListOf<RootProcessInfo>()
        val lines = stdout.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("PID", ignoreCase = true)) continue
            val parts = trimmed.split(Regex("\\s+"), limit = 4)
            if (parts.size < 4) continue
            val pid = parts[0].toIntOrNull() ?: continue
            val rssKb = parts[1].toLongOrNull() ?: 0L
            val user = parts[2]
            val name = parts[3].trim()
            if (name.isNotBlank()) {
                list += RootProcessInfo(pid = pid, rssKb = rssKb, user = user, processName = name)
            }
        }
        return list
    }

    private fun parseLegacyPs(stdout: String): List<RootProcessInfo> {
        val list = mutableListOf<RootProcessInfo>()
        val lines = stdout.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("USER", ignoreCase = true)) continue
            // Typical legacy Android ps: USER PID PPID VSIZE RSS WCHAN PC S NAME
            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size < 9) continue
            val user = parts[0]
            val pid = parts[1].toIntOrNull() ?: continue
            val rssKb = parts[4].toLongOrNull() ?: 0L
            val name = parts.last()
            if (name.isNotBlank()) {
                list += RootProcessInfo(pid = pid, rssKb = rssKb, user = user, processName = name)
            }
        }
        return list
    }
}
