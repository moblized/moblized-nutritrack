package com.moblized.nutritrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moblized.nutritrack.data.AppRelease
import com.moblized.nutritrack.data.Updates
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/** Where an update is up to. */
internal sealed interface UpdateStep {
    data object Ready : UpdateStep
    data class Downloading(val progress: Float) : UpdateStep
    /** Android needs a one-time OK for NutriTrack to install apps. */
    data class NeedsPermission(val apk: File) : UpdateStep
    /** Android's installer is open; the person confirms there. */
    data class Installing(val apk: File) : UpdateStep
    data class Failed(val message: String) : UpdateStep
}

/** Shown over the whole app whenever a newer NutriTrack is out. */
@Composable
fun UpdatePrompt() {
    val offered by Updates.available.collectAsStateWithLifecycle()
    val release = offered ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember(release.versionCode) { mutableStateOf<UpdateStep>(UpdateStep.Ready) }
    var job by remember { mutableStateOf<Job?>(null) }

    // Back from the "Install unknown apps" switch: carry on with the install.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val s = step
        if (s is UpdateStep.NeedsPermission && Updates.canInstall(context)) {
            Updates.install(context, s.apk)
            step = UpdateStep.Installing(s.apk)
        }
    }

    fun installOrAsk(apk: File) {
        if (Updates.canInstall(context)) {
            Updates.install(context, apk)
            step = UpdateStep.Installing(apk)
        } else {
            step = UpdateStep.NeedsPermission(apk)
        }
    }

    fun startDownload() {
        if (release.apkUrl == null) {
            Updates.openReleasePage(context, release)
            return
        }
        step = UpdateStep.Downloading(0f)
        job = scope.launch {
            try {
                val apk = Updates.download(context, release) { p -> step = UpdateStep.Downloading(p) }
                installOrAsk(apk)
            } catch (e: CancellationException) {
                step = UpdateStep.Ready
            } catch (e: Exception) {
                step = UpdateStep.Failed(e.message ?: "The download didn't finish")
            }
        }
    }

    fun close() {
        when (step) {
            is UpdateStep.Ready, is UpdateStep.Failed -> Updates.snooze(context, release)
            is UpdateStep.Downloading -> {}
            else -> {
                Updates.available.value = null
            }
        }
    }

    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        UpdateCard(
            release = release,
            installedName = remember { Updates.installedVersionName(context) },
            step = step,
            onPrimary = {
                when (val s = step) {
                    is UpdateStep.Ready, is UpdateStep.Failed -> startDownload()
                    is UpdateStep.NeedsPermission -> Updates.openInstallPermission(context)
                    is UpdateStep.Installing -> installOrAsk(s.apk)
                    is UpdateStep.Downloading -> Unit
                }
            },
            onSecondary = {
                if (step is UpdateStep.Downloading) {
                    job?.cancel()
                    step = UpdateStep.Ready
                } else {
                    close()
                }
            },
            onOpenPage = { Updates.openReleasePage(context, release) },
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
internal fun UpdateCard(
    release: AppRelease,
    installedName: String,
    step: UpdateStep,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onOpenPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiTile("🎉", MaterialTheme.colorScheme.primaryContainer, size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Update available", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "NutriTrack ${release.versionName} · you have $installedName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            when (step) {
                is UpdateStep.Ready -> {
                    Text("What's new", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        releaseNotes(release.notes),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                    )
                    if (release.apkBytes > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Download: ${String.format(Locale.US, "%.0f", release.apkBytes / 1_000_000.0)} MB. " +
                                "Your diary, foods and settings stay as they are.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is UpdateStep.Downloading -> {
                    Text("Downloading… ${(step.progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { step.progress }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateStep.NeedsPermission -> Text(
                    "One more step, just this once: Android needs your OK for NutriTrack to install its own updates. " +
                        "Turn on \"Allow from this source\", then come back here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                is UpdateStep.Installing -> Text(
                    "Tap Update on Android's screen to finish. NutriTrack restarts on the new version.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                is UpdateStep.Failed -> {
                    Text(step.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onOpenPage, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("Download from the release page instead")
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSecondary) {
                    Text(
                        when (step) {
                            is UpdateStep.Downloading -> "Cancel"
                            is UpdateStep.Ready, is UpdateStep.Failed -> "Later"
                            else -> "Close"
                        },
                    )
                }
                if (step !is UpdateStep.Downloading) {
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onPrimary) {
                        Text(
                            when (step) {
                                is UpdateStep.Ready -> "Update now"
                                is UpdateStep.Failed -> "Try again"
                                is UpdateStep.NeedsPermission -> "Open settings"
                                else -> "Install"
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Commit-style release notes for reading: wrapped lines rejoined, "- " items as bullets. */
internal fun releaseNotes(notes: String): String {
    val out = ArrayList<String>()
    var joinNext = false
    for (raw in notes.lines()) {
        val line = raw.trim()
        if (line.isEmpty()) {
            joinNext = false
            continue
        }
        val bullet = line.startsWith("- ") || line.startsWith("* ")
        when {
            bullet -> out += "• " + line.drop(2)
            joinNext && out.isNotEmpty() -> out[out.lastIndex] = out.last() + " " + line
            else -> out += line
        }
        joinNext = true
    }
    return if (out.isEmpty()) "Fixes and improvements." else out.joinToString("\n")
}
