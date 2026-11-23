package dev.localbuild

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

class BuildException(message: String) : Exception(message)

data class BuildRequest(
    val repoUrl: String,
    val branch: String = "",
    val gradleTask: String = "assembleDebug",
    val token: String = ""
)

class BuildEngine(private val ctx: Context, private val toolchain: ToolchainManager) {

    /** Clone le dépôt, lance Gradle, retourne l'APK copié dans files/outputs/. */
    suspend fun run(req: BuildRequest, log: (String) -> Unit): File = withContext(Dispatchers.IO) {
        val missing = toolchain.missing()
        if (missing.isNotEmpty()) throw BuildException("Toolchain incomplète : ${missing.joinToString()}")

        val job = coroutineContext[Job]!!
        val work = File(ctx.filesDir, "work/${slugOf(req.repoUrl)}")

        // 1. Clone
        log("▶ Clone de ${req.repoUrl}")
        work.deleteRecursively()
        work.parentFile!!.mkdirs()
        try {
            val clone = Git.cloneRepository()
                .setURI(req.repoUrl)
                .setDirectory(work)
                .setProgressMonitor(LogProgress(log) { job.isCancelled })
            if (req.branch.isNotBlank()) clone.setBranch(req.branch)
            // Jeton d'accès (GitHub/GitLab) : passé comme nom d'utilisateur, mot de passe vide.
            if (req.token.isNotBlank()) clone.setCredentialsProvider(UsernamePasswordCredentialsProvider(req.token, ""))
            clone.call().close()
        } catch (e: Exception) {
            throw BuildException("Échec du clone : ${e.message}")
        }

        // 2. Préparation
        val gradlew = File(work, "gradlew")
        if (!gradlew.exists()) throw BuildException("Pas de gradlew à la racine du dépôt.")
        File(work, "local.properties").writeText("sdk.dir=${toolchain.sdkHome.absolutePath}\n")
        val home = File(ctx.filesDir, "home").apply { mkdirs() }
        val gradleHome = File(ctx.filesDir, "gradle-home").apply { mkdirs() }

        // 3. Gradle
        val cmd = listOf(
            "/system/bin/sh", "gradlew", req.gradleTask,
            "--no-daemon", "--console=plain", "--stacktrace",
            // Pas de lib native-platform pour Linux/bionic : sans ça Gradle plante au démarrage.
            "-Dorg.gradle.native=false",
            "-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8",
            // aapt2 de Maven est x86_64 : on force celui de l'appareil.
            "-Pandroid.aapt2FromMavenOverride=${toolchain.aapt2.absolutePath}"
        )
        log("▶ ${cmd.drop(2).joinToString(" ")}")

        val pb = ProcessBuilder(cmd).directory(work).redirectErrorStream(true)
        pb.environment().apply {
            put("JAVA_HOME", toolchain.jdkHome.absolutePath)
            put("ANDROID_HOME", toolchain.sdkHome.absolutePath)
            put("ANDROID_SDK_ROOT", toolchain.sdkHome.absolutePath)
            put("GRADLE_USER_HOME", gradleHome.absolutePath)
            put("HOME", home.absolutePath)
            put("TMPDIR", ctx.cacheDir.absolutePath)
            put("PATH", "${toolchain.jdkHome}/bin:/system/bin:/system/xbin")
        }
        val process = pb.start()
        // Annulation : détruire le processus débloque la lecture ci-dessous.
        val handle = job.invokeOnCompletion { if (process.isAlive) process.destroyForcibly() }
        try {
            process.inputStream.bufferedReader().forEachLine { log(it) }
            val code = process.waitFor()
            if (job.isCancelled) throw kotlinx.coroutines.CancellationException("Build annulé")
            if (code != 0) throw BuildException("Gradle a échoué (code $code) — voir les logs.")
        } finally {
            handle.dispose()
            if (process.isAlive) process.destroyForcibly()
        }

        // 4. Récupération de l'APK
        val apk = work.walkTopDown()
            .onEnter { it.name != ".git" }
            .filter {
                it.isFile && it.extension == "apk" &&
                    it.path.contains("build/outputs/apk") && !it.path.contains("androidTest")
            }
            .maxByOrNull { it.lastModified() }
            ?: throw BuildException("Build réussi mais aucun APK trouvé dans build/outputs/apk.")

        val outDir = File(ctx.filesDir, "outputs").apply { mkdirs() }
        val dest = File(outDir, "${slugOf(req.repoUrl)}-${apk.name}")
        apk.copyTo(dest, overwrite = true)
        log("✔ APK : ${dest.name} (${dest.length() / 1024} Ko)")
        dest
    }

    private fun slugOf(url: String) =
        url.trimEnd('/').removeSuffix(".git").substringAfter("://")
            .replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80)

    // Interface ProgressMonitor de JGit 5.13.3 : exactement ces 5 méthodes (vérifié avec javap).
    private class LogProgress(
        private val log: (String) -> Unit,
        private val cancelled: () -> Boolean
    ) : ProgressMonitor {
        override fun start(totalTasks: Int) {}
        override fun beginTask(title: String?, totalWork: Int) { log("   git : $title") }
        override fun update(completed: Int) {}
        override fun endTask() {}
        override fun isCancelled() = cancelled()
    }
}
