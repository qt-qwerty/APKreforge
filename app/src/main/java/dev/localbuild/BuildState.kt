package dev.localbuild

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

sealed interface Status {
    data object Idle : Status
    data class Working(val label: String) : Status
    data class Success(val apkPath: String) : Status
    data class Failed(val message: String) : Status
}

/** État partagé entre le service (qui travaille) et l'UI (qui observe). */
object BuildState {
    val status = MutableStateFlow<Status>(Status.Idle)
    val logs = MutableStateFlow<List<String>>(emptyList())

    fun log(line: String) = logs.update { (it + line).takeLast(3000) }
    fun clearLogs() { logs.value = emptyList() }
}

/** Réglages persistants (URLs de la toolchain, dépôt, jeton). */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("localbuild", Context.MODE_PRIVATE)

    fun get(key: String, def: String = ""): String = sp.getString(key, def) ?: def
    fun set(key: String, value: String) = sp.edit().putString(key, value).apply()
    fun getBool(key: String, def: Boolean = false): Boolean = sp.getBoolean(key, def)
    fun setBool(key: String, value: Boolean) = sp.edit().putBoolean(key, value).apply()

    companion object {
        const val XMX = "xmx_mb"
        const val EXTRA_ARGS = "extra_args"
        const val OFFLINE = "offline"
        const val CLEAN_AFTER = "clean_after"
        const val REPO = "repo"
        const val BRANCH = "branch"
        const val TASK = "task"
        const val TOKEN = "token"
        const val JDK_URL = "jdk_url"
        const val SDK_URL = "sdk_url"
        const val AAPT2_URL = "aapt2_url"
    }
}
