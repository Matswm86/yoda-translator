package no.mwm.yoda.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.mwm.yoda.engine.Lang
import no.mwm.yoda.engine.LanguageDetector
import no.mwm.yoda.engine.TranslationEngine
import no.mwm.yoda.engine.TranslationResult
import no.mwm.yoda.engine.YodaStyle
import no.mwm.yoda.engine.llm.OnDeviceModelEngine

/** Null means "detect from the text". */
private val LANG_CHOICES: List<Lang?> = listOf(null, Lang.NO, Lang.EN)

@Composable
fun YodaScreen(
    engine: TranslationEngine,
    llmEngine: OnDeviceModelEngine,
    initialText: String = ""
) {
    var input by remember { mutableStateOf(initialText) }
    var langChoice by remember { mutableStateOf<Lang?>(null) }
    var style by remember { mutableStateOf(YodaStyle.CLASSIC) }
    var compareAll by remember { mutableStateOf(false) }

    val effectiveLang = langChoice ?: LanguageDetector.detect(input)

    val result: TranslationResult? = remember(input, effectiveLang, style) {
        if (input.isBlank()) null else engine.translate(input, effectiveLang, style)
    }
    val allStyles = remember(input, effectiveLang, compareAll) {
        if (!compareAll || input.isBlank()) emptyList()
        else YodaStyle.entries.map { it to engine.translate(input, effectiveLang, it) }
    }

    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // The model takes seconds per sentence, so it runs after typing pauses
    // and off the main thread. The rule engine keeps answering live below it.
    var modelReady by remember { mutableStateOf(llmEngine.isAvailable()) }
    var modelBusy by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var modelResult by remember { mutableStateOf<TranslationResult?>(null) }
    LaunchedEffect(input, effectiveLang, modelReady) {
        modelResult = null
        if (!modelReady || input.isBlank()) return@LaunchedEffect
        delay(700)
        modelBusy = true
        try {
            modelResult = withContext(Dispatchers.Default) {
                llmEngine.translate(input, effectiveLang, YodaStyle.CLASSIC)
            }
        } finally {
            modelBusy = false
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            val error = withContext(Dispatchers.IO) {
                runCatching { llmEngine.importModel(uri) }.exceptionOrNull()
            }
            importing = false
            modelReady = llmEngine.isAvailable()
            snackbar.showSnackbar(error?.let { "Import failed: ${it.message}" } ?: "Model imported")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Header(effectiveLang, langChoice == null)

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Original") },
                placeholder = { Text("Du vil lære tålmodighet hvis du trener hardt hver dag.") },
                minLines = 3,
                shape = RoundedCornerShape(14.dp),
                trailingIcon = {
                    if (input.isNotEmpty()) {
                        IconButton(onClick = { input = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                }
            )

            Spacer(Modifier.height(14.dp))
            LanguageRow(langChoice, effectiveLang) { langChoice = it }

            Spacer(Modifier.height(14.dp))
            StyleRow(style) { style = it }
            Text(
                style.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(18.dp))

            if (modelReady && input.isNotBlank()) {
                val m = modelResult
                OutputCard(
                    title = "Yoda (model)",
                    text = when {
                        m != null -> m.output
                        modelBusy -> "Thinking, the model is…"
                        else -> "…"
                    },
                    onCopy = {
                        m?.let { clipboard.setText(AnnotatedString(it.output)) }
                        scope.launch { snackbar.showSnackbar("Copied") }
                    }
                )
                m?.notes?.forEach { note ->
                    Text(
                        "• $note",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            if (result != null) {
                OutputCard(
                    title = if (modelReady) "Rule engine" else "Yoda",
                    subdued = modelReady,
                    text = result.output,
                    onCopy = {
                        clipboard.setText(AnnotatedString(result.output))
                        scope.launch { snackbar.showSnackbar("Copied") }
                    }
                )
                Spacer(Modifier.height(12.dp))
                Diagnostics(result)
            } else {
                Text(
                    "Type something. Translate as you go, I will.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(18.dp))
            AssistChip(
                onClick = { compareAll = !compareAll },
                label = { Text(if (compareAll) "Hide style comparison" else "Compare all three styles") },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.primary
                )
            )

            if (allStyles.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "The rule engine's three styles, side by side.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                allStyles.forEach { (s, r) ->
                    OutputCard(
                        title = s.label,
                        text = r.output,
                        subdued = s != style,
                        onCopy = {
                            clipboard.setText(AnnotatedString(r.output))
                            scope.launch { snackbar.showSnackbar("Copied") }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(22.dp))
            EngineFooter(
                active = engine,
                llm = llmEngine,
                modelReady = modelReady,
                importing = importing,
                onImport = { importer.launch(arrayOf("*/*")) }
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Header(lang: Lang, autoDetected: Boolean) {
    Column {
        Text(
            "Yoda-speak",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            if (autoDetected) "Detected: ${lang.label}" else "Forced: ${lang.label}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LanguageRow(choice: Lang?, effective: Lang, onPick: (Lang?) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        LANG_CHOICES.forEachIndexed { i, l ->
            SegmentedButton(
                selected = choice == l,
                onClick = { onPick(l) },
                shape = SegmentedButtonDefaults.itemShape(i, LANG_CHOICES.size),
                label = {
                    Text(
                        when (l) {
                            null -> if (choice == null) "Auto (${effective.label})" else "Auto"
                            else -> l.label
                        },
                        fontSize = 13.sp
                    )
                }
            )
        }
    }
}

@Composable
private fun StyleRow(current: YodaStyle, onPick: (YodaStyle) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        YodaStyle.entries.forEach { s ->
            FilterChip(
                selected = s == current,
                onClick = { onPick(s) },
                label = { Text(s.label, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun OutputCard(
    title: String,
    text: String,
    subdued: Boolean = false,
    onCopy: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (subdued) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCopy, modifier = Modifier.height(24.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text.ifBlank { "—" },
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Serif
            )
        }
    }
}

@Composable
private fun Diagnostics(r: TranslationResult) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (r.morphologyPreserved) "Word forms intact" else "WORD FORMS CHANGED",
                style = MaterialTheme.typography.labelMedium,
                color = if (r.morphologyPreserved) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Parse ${(r.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { r.confidence },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline
        )
        r.notes.forEach { note ->
            Text(
                "• $note",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun EngineFooter(
    active: TranslationEngine,
    llm: TranslationEngine,
    modelReady: Boolean,
    importing: Boolean,
    onImport: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            "Engine: ${active.displayName}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                importing -> "Copying the model file (about 1 GB), this takes a minute…"
                modelReady -> "${llm.displayName}: ready."
                else -> llm.unavailableReason() ?: ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onImport, enabled = !importing) {
            Text(if (modelReady) "Replace model file…" else "Import model file…")
        }
    }
}
