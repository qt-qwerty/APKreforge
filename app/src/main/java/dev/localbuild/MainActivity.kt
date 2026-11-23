package dev.localbuild

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }
}

@Composable
private fun Screen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val status by BuildState.status.collectAsState()
    val logs by BuildState.logs.collectAsState()

    var repo by remember { mutableStateOf(prefs.get(Prefs.REPO)) }
    var branch by remember { mutableStateOf(prefs.get(Prefs.BRANCH)) }
    var task by remember { mutableStateOf(prefs.get(Prefs.TASK, "assembleDebug")) }
    var token by remember { mutableStateOf(prefs.get(Prefs.TOKEN)) }
    var jdk by remember { mutableStateOf(prefs.get(Prefs.JDK_URL)) }
    var sdk by remember { mutableStateOf(prefs.get(Prefs.SDK_URL)) }
    var aapt2 by remember { mutableStateOf(prefs.get(Prefs.AAPT2_URL)) }

    // Recalculé quand le statut change (fin d'une installation de toolchain, par ex.)
    val missing = remember(status) { ToolchainManager(ctx).missing() }
    val busy = status is Status.Working

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            Modifier.weight(0.5f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("LocalBuild", style = MaterialTheme.typography.headlineSmall)

            Field("URL du dépôt Git (https)", repo) { repo = it; prefs.set(Prefs.REPO, it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Branche (optionnel)", branch, Modifier.weight(1f)) { branch = it; prefs.set(Prefs.BRANCH, it) }
                Field("Tâche Gradle", task, Modifier.weight(1f)) { task = it; prefs.set(Prefs.TASK, it) }
            }
            Field("Jeton d'accès (dépôt privé, optionnel)", token, secret = true) {
                token = it; prefs.set(Prefs.TOKEN, it)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !busy && repo.isNotBlank() && missing.isEmpty(),
                    onClick = {
                        send(ctx, BuildService.ACTION_BUILD) {
                            putExtra(BuildService.EXTRA_REPO, repo.trim())
                            putExtra(BuildService.EXTRA_BRANCH, branch.trim())
                            putExtra(BuildService.EXTRA_TASK, task.trim())
                            putExtra(BuildService.EXTRA_TOKEN, token.trim())
                        }
                    }
                ) { Text("Compiler") }
                OutlinedButton(enabled = busy, onClick = { send(ctx, BuildService.ACTION_CANCEL) }) {
                    Text("Annuler")
                }
            }

            when (val s = status) {
                is Status.Working -> Text("⏳ ${s.label}")
                is Status.Failed -> Text("✖ ${s.message}", color = MaterialTheme.colorScheme.error)
                is Status.Success -> {
                    Text("✔ Build terminé")
                    Button(onClick = { ApkInstaller.install(ctx, File(s.apkPath)) }) { Text("Installer l'APK") }
                }
                Status.Idle -> {}
            }

            Text("Toolchain", style = MaterialTheme.typography.titleMedium)
            Text(
                if (missing.isEmpty()) "✔ Prête" else "Manquant : ${missing.joinToString()}",
                style = MaterialTheme.typography.bodySmall
            )
            Field("URL JDK 17 Android (.tar.gz/.tar.xz/.zip)", jdk) { jdk = it; prefs.set(Prefs.JDK_URL, it) }
            Field("URL Android SDK (platforms + build-tools)", sdk) { sdk = it; prefs.set(Prefs.SDK_URL, it) }
            Field("URL aapt2 aarch64", aapt2) { aapt2 = it; prefs.set(Prefs.AAPT2_URL, it) }
            OutlinedButton(
                enabled = !busy && (jdk.isNotBlank() || sdk.isNotBlank() || aapt2.isNotBlank()),
                onClick = {
                    send(ctx, BuildService.ACTION_TOOLCHAIN) {
                        putExtra(BuildService.EXTRA_JDK, jdk.trim())
                        putExtra(BuildService.EXTRA_SDK, sdk.trim())
                        putExtra(BuildService.EXTRA_AAPT2, aapt2.trim())
                    }
                }
            ) { Text("Installer / mettre à jour la toolchain") }
        }

        val listState = rememberLazyListState()
        LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.scrollToItem(logs.lastIndex) }
        LazyColumn(Modifier.weight(0.5f).fillMaxWidth(), state = listState) {
            items(logs) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = modifier.fillMaxWidth()
    )
}

private fun send(ctx: Context, action: String, extras: Intent.() -> Unit = {}) {
    val i = Intent(ctx, BuildService::class.java).setAction(action).apply(extras)
    ctx.startForegroundService(i)
}
