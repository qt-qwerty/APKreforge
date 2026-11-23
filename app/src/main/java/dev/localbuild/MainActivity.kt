package dev.localbuild

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ForgeTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App() }
            }
        }
    }
}

@Composable
private fun App() {
    var settings by remember { mutableStateOf(false) }
    BackHandler(enabled = settings) { settings = false }
    if (settings) SettingsScreen(onBack = { settings = false })
    else MainScreen(onSettings = { settings = true })
}

// ───────────────────────── Écran principal ─────────────────────────

@Composable
private fun MainScreen(onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val status by BuildState.status.collectAsState()
    val logs by BuildState.logs.collectAsState()

    var repo by remember { mutableStateOf(prefs.get(Prefs.REPO)) }
    var showLogs by remember { mutableStateOf(false) }

    // Recalculé à chaque changement de statut (fin d'une installation de toolchain, par ex.)
    val missing = remember(status) { ToolchainManager(ctx).missing() }
    val st = status
    val busy = st is Status.Working

    val (title, sub) = when (st) {
        Status.Idle -> "Prêt à forger" to
            (if (missing.isEmpty()) "Colle l'URL d'un dépôt Git" else "Toolchain à installer dans les paramètres")
        is Status.Working -> "Forge en cours…" to logs.lastOrNull().orEmpty().trim()
        is Status.Success -> "APK forgé" to File(st.apkPath).name
        is Status.Failed -> "Échec de la forge" to st.message
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "LocalBuild",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSettings) { Text("Paramètres") }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (showLogs) {
                LogPanel(logs)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ForgeAnimation(active = busy, modifier = Modifier.size(210.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (st is Status.Failed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (logs.isNotEmpty()) {
            TextButton(
                onClick = { showLogs = !showLogs },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text(if (showLogs) "Masquer les logs" else "Afficher les logs") }
        }

        OutlinedTextField(
            value = repo,
            onValueChange = { repo = it; prefs.set(Prefs.REPO, it) },
            label = { Text("URL du dépôt") },
            placeholder = { Text("https://github.com/utilisateur/projet") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        when {
            busy -> OutlinedButton(
                onClick = { send(ctx, BuildService.ACTION_CANCEL) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Annuler") }

            else -> {
                if (st is Status.Success) {
                    Button(
                        onClick = { ApkInstaller.install(ctx, File(st.apkPath)) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Installer l'APK") }
                    Spacer(Modifier.height(8.dp))
                }
                val forge = {
                    send(ctx, BuildService.ACTION_BUILD) { putExtra(BuildService.EXTRA_REPO, repo.trim()) }
                }
                if (st is Status.Success) {
                    OutlinedButton(
                        onClick = forge,
                        enabled = repo.isNotBlank() && missing.isEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Forger à nouveau") }
                } else {
                    Button(
                        onClick = forge,
                        enabled = repo.isNotBlank() && missing.isEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Forger l'APK") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LogPanel(logs: List<String>) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.scrollToItem(logs.lastIndex) }
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        items(logs) {
            Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFD8CBBE))
        }
    }
}

// ───────────────────────── Paramètres avancés ─────────────────────────

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val status by BuildState.status.collectAsState()
    val scope = rememberCoroutineScope()

    var branch by remember { mutableStateOf(prefs.get(Prefs.BRANCH)) }
    var token by remember { mutableStateOf(prefs.get(Prefs.TOKEN)) }
    var task by remember { mutableStateOf(prefs.get(Prefs.TASK, "assembleDebug")) }
    var xmx by remember { mutableStateOf(prefs.get(Prefs.XMX, "1536")) }
    var extra by remember { mutableStateOf(prefs.get(Prefs.EXTRA_ARGS)) }
    var offline by remember { mutableStateOf(prefs.getBool(Prefs.OFFLINE)) }
    var clean by remember { mutableStateOf(prefs.getBool(Prefs.CLEAN_AFTER)) }
    var jdk by remember { mutableStateOf(prefs.get(Prefs.JDK_URL)) }
    var sdk by remember { mutableStateOf(prefs.get(Prefs.SDK_URL)) }
    var aapt2 by remember { mutableStateOf(prefs.get(Prefs.AAPT2_URL)) }
    var storageMsg by remember { mutableStateOf("") }

    val missing = remember(status) { ToolchainManager(ctx).missing() }
    val busy = status is Status.Working

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Retour") }
            Text("Paramètres avancés", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Section("Dépôt")
            Field("Branche (vide = branche par défaut)", branch) { branch = it; prefs.set(Prefs.BRANCH, it) }
            Field("Jeton d'accès (dépôts privés)", token, secret = true) { token = it; prefs.set(Prefs.TOKEN, it) }

            Section("Compilation")
            Field("Tâche Gradle", task) { task = it; prefs.set(Prefs.TASK, it) }
            Field("Mémoire de la JVM (Mo)", xmx, keyboard = KeyboardType.Number) {
                xmx = it.filter { c -> c.isDigit() }; prefs.set(Prefs.XMX, xmx)
            }
            Field("Arguments Gradle supplémentaires", extra) { extra = it; prefs.set(Prefs.EXTRA_ARGS, it) }
            ToggleRow("Mode hors-ligne", "Gradle n'utilise que son cache local", offline) {
                offline = it; prefs.setBool(Prefs.OFFLINE, it)
            }
            ToggleRow("Nettoyer après le build", "Supprime les sources clonées une fois l'APK copié", clean) {
                clean = it; prefs.setBool(Prefs.CLEAN_AFTER, it)
            }

            Section("Toolchain")
            Text(
                if (missing.isEmpty()) "✔ Prête" else "Manquant : ${missing.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Field("URL du JDK 17 Android (.tar.gz / .tar.xz / .zip)", jdk) { jdk = it; prefs.set(Prefs.JDK_URL, it) }
            Field("URL du SDK Android (platforms + build-tools)", sdk) { sdk = it; prefs.set(Prefs.SDK_URL, it) }
            Field("URL d'aapt2 aarch64", aapt2) { aapt2 = it; prefs.set(Prefs.AAPT2_URL, it) }
            OutlinedButton(
                enabled = !busy && (jdk.isNotBlank() || sdk.isNotBlank() || aapt2.isNotBlank()),
                onClick = {
                    send(ctx, BuildService.ACTION_TOOLCHAIN) {
                        putExtra(BuildService.EXTRA_JDK, jdk.trim())
                        putExtra(BuildService.EXTRA_SDK, sdk.trim())
                        putExtra(BuildService.EXTRA_AAPT2, aapt2.trim())
                    }
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Installer / mettre à jour la toolchain") }

            Section("Stockage")
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            File(ctx.filesDir, "work").deleteRecursively()
                            File(ctx.filesDir, "gradle-home").deleteRecursively()
                        }
                        storageMsg = "Sources clonées et cache Gradle supprimés."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Vider le cache Gradle et les sources") }
            if (storageMsg.isNotEmpty()) {
                Text(storageMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp)
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun send(ctx: Context, action: String, extras: Intent.() -> Unit = {}) {
    val i = Intent(ctx, BuildService::class.java).setAction(action).apply(extras)
    ctx.startForegroundService(i)
}
