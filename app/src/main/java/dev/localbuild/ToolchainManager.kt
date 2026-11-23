package dev.localbuild

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipInputStream

/**
 * Télécharge et installe la toolchain dans le stockage privé de l'app :
 *   toolchain/jdk          -> JDK 17 compilé pour Android (bionic), aarch64
 *   toolchain/android-sdk  -> SDK : platforms/android-XX, build-tools/XX (+ platform-tools si besoin)
 *   toolchain/aapt2        -> aapt2 natif aarch64 (les binaires du SDK officiel sont x86 uniquement)
 */
class ToolchainManager(private val ctx: android.content.Context) {

    enum class Component(val dir: String, val label: String) {
        JDK("jdk", "JDK"),
        SDK("android-sdk", "Android SDK"),
        AAPT2("aapt2", "aapt2")
    }

    val root = File(ctx.filesDir, "toolchain")
    val jdkHome = File(root, "jdk")
    val sdkHome = File(root, "android-sdk")
    val aapt2: File get() = File(root, "aapt2/aapt2")

    /** Liste lisible de ce qui manque ; vide = prêt à compiler. */
    fun missing(): List<String> = buildList {
        if (!File(jdkHome, "bin/java").exists()) add("JDK (bin/java)")
        if (!File(sdkHome, "platforms").isDirectory) add("Android SDK (platforms/)")
        if (!File(sdkHome, "build-tools").isDirectory) add("Android SDK (build-tools/)")
        if (!aapt2.exists()) add("aapt2")
    }

    fun isReady() = missing().isEmpty()

    suspend fun install(urls: Map<Component, String>, log: (String) -> Unit) =
        withContext(Dispatchers.IO) {
            root.mkdirs()
            for ((comp, url) in urls) {
                if (url.isBlank()) continue
                val target = File(root, comp.dir)
                val tmp = File(ctx.cacheDir, "dl-${comp.dir}")
                try {
                    log("▶ ${comp.label} : téléchargement")
                    download(url, tmp, log)
                    log("▶ ${comp.label} : extraction")
                    target.deleteRecursively()
                    target.mkdirs()
                    extract(tmp, target, url)
                    flattenSingleDir(target)
                    fixPermissions(target)
                    log("✔ ${comp.label} installé")
                } finally {
                    tmp.delete()
                }
            }
        }

    private fun download(url: String, out: File, log: (String) -> Unit) {
        val conn = URL(url).openConnection().apply {
            connectTimeout = 20_000
            readTimeout = 60_000
        }
        val total = conn.contentLengthLong
        var done = 0L
        var lastPct = -10
        conn.getInputStream().use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val pct = (done * 100 / total).toInt()
                        if (pct >= lastPct + 10) { lastPct = pct; log("   $pct %") }
                    }
                }
            }
        }
    }

    private fun extract(archive: File, dest: File, url: String) {
        val name = url.substringBefore('?').lowercase()
        val raw = BufferedInputStream(archive.inputStream())
        when {
            name.endsWith(".zip") -> extractZip(raw, dest)
            name.endsWith(".tar.gz") || name.endsWith(".tgz") ->
                extractTar(GzipCompressorInputStream(raw), dest)
            name.endsWith(".tar.xz") -> extractTar(XZCompressorInputStream(raw), dest)
            name.endsWith(".tar") -> extractTar(raw, dest)
            else -> { raw.close(); error("Format d'archive non géré : $name (zip, tar, tar.gz, tar.xz)") }
        }
    }

    private fun extractZip(input: InputStream, dest: File) {
        ZipInputStream(input).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val out = safeResolve(dest, e.name)
                if (e.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
                e = zip.nextEntry
            }
        }
    }

    private fun extractTar(input: InputStream, dest: File) {
        TarArchiveInputStream(input).use { tar ->
            var e = tar.nextTarEntry
            while (e != null) {
                val out = safeResolve(dest, e.name)
                when {
                    e.isDirectory -> out.mkdirs()
                    e.isSymbolicLink -> {
                        out.parentFile?.mkdirs()
                        Files.deleteIfExists(out.toPath())
                        Files.createSymbolicLink(out.toPath(), Paths.get(e.linkName))
                    }
                    else -> {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { tar.copyTo(it) }
                        if ((e.mode and 0b001_001_001) != 0) out.setExecutable(true, false)
                    }
                }
                e = tar.nextTarEntry
            }
        }
    }

    /** Protège contre le « zip slip » (entrées du type ../../etc). */
    private fun safeResolve(dest: File, name: String): File {
        val f = File(dest, name).canonicalFile
        require(f.path.startsWith(dest.canonicalPath + File.separator) || f == dest.canonicalFile) {
            "Entrée d'archive refusée : $name"
        }
        return f
    }

    /** Beaucoup d'archives contiennent un unique dossier racine (jdk-17.0.x/...) : on le remonte. */
    private fun flattenSingleDir(dir: File) {
        val children = dir.listFiles() ?: return
        val only = children.singleOrNull() ?: return
        if (!only.isDirectory) return
        val tmp = File(dir.parentFile, dir.name + ".tmp")
        tmp.deleteRecursively()
        if (!only.renameTo(tmp)) return
        dir.deleteRecursively()
        tmp.renameTo(dir)
    }

    private fun fixPermissions(dir: File) {
        val names = setOf("java", "javac", "aapt2", "aapt", "zipalign", "d8", "apksigner", "jspawnhelper")
        dir.walkTopDown().filter { it.isFile }.forEach {
            if (it.parentFile?.name == "bin" || it.name in names) it.setExecutable(true, false)
        }
    }
}
