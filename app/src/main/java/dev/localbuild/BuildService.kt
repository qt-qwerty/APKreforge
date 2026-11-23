package dev.localbuild

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Service au premier plan : le build continue même si l'app passe en arrière-plan. */
class BuildService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() impose d'appeler startForeground() dans les 5 s, quelle que soit l'action.
        startForeground(NOTIF_ID, notification("LocalBuild"))
        when (intent?.action) {
            ACTION_CANCEL -> if (job?.isActive == true) job?.cancel() else finish()
            ACTION_BUILD -> if (job?.isActive != true) {
                // L'URL vient de l'écran principal ; tout le reste des réglages avancés.
                val p = Prefs(this)
                val req = BuildRequest(
                    repoUrl = intent.getStringExtra(EXTRA_REPO).orEmpty(),
                    branch = p.get(Prefs.BRANCH).trim(),
                    gradleTask = p.get(Prefs.TASK).trim().ifBlank { "assembleDebug" },
                    token = p.get(Prefs.TOKEN).trim(),
                    xmxMb = (p.get(Prefs.XMX).trim().toIntOrNull() ?: 1536).coerceIn(512, 8192),
                    extraArgs = p.get(Prefs.EXTRA_ARGS),
                    offline = p.getBool(Prefs.OFFLINE),
                    cleanAfter = p.getBool(Prefs.CLEAN_AFTER)
                )
                job = scope.launch { doBuild(req) }
            } else stopIfIdle()
            ACTION_TOOLCHAIN -> if (job?.isActive != true) {
                val urls = mapOf(
                    ToolchainManager.Component.JDK to intent.getStringExtra(EXTRA_JDK).orEmpty(),
                    ToolchainManager.Component.SDK to intent.getStringExtra(EXTRA_SDK).orEmpty(),
                    ToolchainManager.Component.AAPT2 to intent.getStringExtra(EXTRA_AAPT2).orEmpty()
                )
                job = scope.launch { doToolchain(urls) }
            } else stopIfIdle()
        }
        return START_NOT_STICKY
    }

    private suspend fun doBuild(req: BuildRequest) {
        acquireWakeLock()
        BuildState.clearLogs()
        BuildState.status.value = Status.Working("Build de ${req.repoUrl}")
        try {
            val apk = BuildEngine(this, ToolchainManager(this)).run(req, BuildState::log)
            BuildState.status.value = Status.Success(apk.absolutePath)
        } catch (e: CancellationException) {
            BuildState.log("■ Annulé")
            BuildState.status.value = Status.Idle
        } catch (e: Exception) {
            BuildState.log("✖ ${e.message}")
            BuildState.status.value = Status.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            releaseWakeLock()
            finish()
        }
    }

    private suspend fun doToolchain(urls: Map<ToolchainManager.Component, String>) {
        acquireWakeLock()
        BuildState.clearLogs()
        BuildState.status.value = Status.Working("Installation de la toolchain")
        try {
            ToolchainManager(this).install(urls, BuildState::log)
            BuildState.status.value = Status.Idle
        } catch (e: CancellationException) {
            BuildState.log("■ Annulé")
            BuildState.status.value = Status.Idle
        } catch (e: Exception) {
            BuildState.log("✖ ${e.message}")
            BuildState.status.value = Status.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            releaseWakeLock()
            finish()
        }
    }

    private fun finish() {
        stopForeground(true)
        stopSelf()
    }

    /** Un travail est déjà en cours : la nouvelle demande est ignorée. */
    private fun stopIfIdle() {}

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "localbuild:build").apply {
            acquire(2 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Builds", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("LocalBuild")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        const val ACTION_BUILD = "dev.localbuild.BUILD"
        const val ACTION_CANCEL = "dev.localbuild.CANCEL"
        const val ACTION_TOOLCHAIN = "dev.localbuild.TOOLCHAIN"
        const val EXTRA_REPO = "repo"
        const val EXTRA_BRANCH = "branch"
        const val EXTRA_TASK = "task"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_JDK = "jdk"
        const val EXTRA_SDK = "sdk"
        const val EXTRA_AAPT2 = "aapt2"
        private const val CHANNEL = "builds"
        private const val NOTIF_ID = 1
    }
}
