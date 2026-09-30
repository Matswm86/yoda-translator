package no.mwm.yoda.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.mwm.yoda.R
import no.mwm.yoda.engine.Lang
import no.mwm.yoda.engine.LanguageDetector
import no.mwm.yoda.engine.TranslationEngine
import no.mwm.yoda.engine.TranslationResult
import no.mwm.yoda.engine.YodaStyle
import no.mwm.yoda.engine.llm.OnDeviceModelEngine
import kotlin.random.Random

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

    val context = LocalContext.current
    val sounds = remember { YodaSounds(context) }
    DisposableEffect(sounds) { onDispose { sounds.release() } }
    val voice = remember { YodaVoice(context) }
    DisposableEffect(voice) { onDispose { voice.stop() } }

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

    // The bubble shows what you type while you edit, and Yoda's answer once
    // you tap Speak. Text arriving from the share sheet opens straight on his answer.
    var editing by remember { mutableStateOf(initialText.isBlank()) }
    val focus = remember { FocusRequester() }
    var focusOnEdit by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val m = modelResult
    val spoken: String? = when {
        input.isBlank() -> null
        modelReady && m != null -> m.output
        else -> result?.output
    }
    val copy: (String) -> Unit = { text ->
        clipboard.setText(AnnotatedString(text))
        scope.launch { snackbar.showSnackbar("Copied") }
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
            Stage(
                lang = effectiveLang,
                autoDetected = langChoice == null,
                input = input,
                onInput = { input = it },
                editing = editing || input.isBlank(),
                focus = focus,
                focusOnEdit = focusOnEdit,
                onEdit = {
                    focusOnEdit = true
                    editing = true
                },
                onSpeak = {
                    if (input.isNotBlank()) {
                        editing = false
                        keyboard?.hide()
                        // Nothing keeps focus, so a hardware Enter's key-up cannot tap the bubble open again.
                        focusManager.clearFocus()
                    }
                },
                spoken = spoken,
                speakerLabel = if (modelReady && m != null) "Yoda model" else "Rule engine",
                thinking = modelReady && modelBusy,
                onCopy = { spoken?.let(copy) },
                // With the model loaded, Yoda waits for its answer instead of voicing the rule engine's first.
                answerFinal = !(modelReady && m == null),
                sounds = sounds,
                voice = voice
            )
            m?.notes?.forEach { note ->
                Text(
                    "• $note",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel("Language")
            LanguageRow(langChoice, effectiveLang) { langChoice = it }

            Spacer(Modifier.height(14.dp))
            SectionLabel("Style")
            StyleRow(style) { style = it }
            Text(
                style.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            if (result != null) {
                Spacer(Modifier.height(18.dp))
                // With the model in the bubble, the rule engine's line stays here for comparison.
                if (modelReady && m != null) {
                    OutputCard(
                        title = "Rule engine",
                        subdued = true,
                        text = result.output,
                        onCopy = { copy(result.output) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Diagnostics(result)
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
                        onCopy = { copy(r.output) }
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
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

/** Yoda on his swamp backdrop, with the speech bubble above his head. */
@Composable
private fun Stage(
    lang: Lang,
    autoDetected: Boolean,
    input: String,
    onInput: (String) -> Unit,
    editing: Boolean,
    focus: FocusRequester,
    focusOnEdit: Boolean,
    onEdit: () -> Unit,
    onSpeak: () -> Unit,
    spoken: String?,
    speakerLabel: String,
    thinking: Boolean,
    onCopy: () -> Unit,
    answerFinal: Boolean,
    sounds: YodaSounds,
    voice: YodaVoice
) {
    val stage = if (isSystemInDarkTheme()) DarkStage else LightStage

    // Reveal Yoda's line a letter at a time; he nods while the text is still coming.
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(spoken, editing) {
        shown = 0
        val line = spoken
        if (line == null || editing) return@LaunchedEffect
        for (i in 1..line.length) {
            shown = i
            delay(if (line[i - 1] in ",.!?") 140L else 34L)
        }
    }
    val talking = !editing && spoken != null && shown < spoken.length

    // A film clip is playing: a murmur, his laugh, or a quote with its words in [caption].
    var voicing by remember { mutableStateOf(false) }
    var humming by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var voiceNote by remember { mutableStateOf<String?>(null) }
    var replays by remember { mutableIntStateOf(0) }
    var caption by remember { mutableStateOf<String?>(null) }
    var muted by remember { mutableStateOf(sounds.muted) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // While nothing else is happening, he hums or laughs to himself every 12 to 25 seconds.
    // The loop only runs while the app is on screen.
    LaunchedEffect(talking, voicing, speaking, muted) {
        if (talking || voicing || speaking || muted) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(Random.nextLong(12_000, 25_000))
                val clip = sounds.nextIdle()
                if (sounds.play(clip)) {
                    humming = true
                    try {
                        delay(clip.millis)
                    } finally {
                        humming = false
                    }
                }
            }
        }
    }
    // He hums once as he starts to answer.
    LaunchedEffect(editing) {
        if (!editing && spoken != null) sounds.play(sounds.murmurs.random())
    }
    // Then he says his answer out loud, fetched through the voice relay.
    LaunchedEffect(spoken, editing, answerFinal, muted, replays) {
        voiceNote = null
        val line = spoken
        if (editing || line == null || !answerFinal || muted) {
            voice.stop()
            return@LaunchedEffect
        }
        try {
            val file = voice.fetch(line)
            sounds.stop()
            speaking = true
            voice.play(file)
        } catch (e: YodaVoice.VoiceError) {
            voiceNote = e.message
        } finally {
            speaking = false
        }
    }
    var lastQuote by remember { mutableStateOf<YodaClip?>(null) }
    val sayQuote: () -> Unit = {
        val clip = (sounds.quotes - setOfNotNull(lastQuote)).random()
        lastQuote = clip
        if (sounds.play(clip)) {
            scope.launch {
                voicing = true
                caption = clip.text
                delay(clip.millis + 400)
                if (caption == clip.text) {
                    caption = null
                    voicing = false
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(stage.top, stage.bottom)))
            .drawBehind {
                val glowCenter = Offset(size.width / 2, size.height * 0.78f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(stage.glow.copy(alpha = 0.55f), Color.Transparent),
                        center = glowCenter,
                        radius = size.width * 0.6f
                    ),
                    radius = size.width * 0.6f,
                    center = glowCenter
                )
            }
            // No bottom padding: the robe runs off the stage edge.
            .padding(start = 16.dp, top = 16.dp, end = 16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Yoda-speak",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (autoDetected) "Detected: ${lang.label}" else "Forced: ${lang.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = {
                    muted = !muted
                    sounds.muted = muted
                    if (muted) {
                        caption = null
                        voicing = false
                    }
                }) {
                    Icon(
                        if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (muted) "Unmute Yoda" else "Mute Yoda",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SpeechBubble(
                input = input,
                onInput = onInput,
                editing = editing,
                focus = focus,
                focusOnEdit = focusOnEdit,
                onEdit = onEdit,
                onSpeak = onSpeak,
                spoken = spoken,
                shown = shown,
                speakerLabel = voiceNote ?: speakerLabel,
                thinking = thinking,
                onCopy = onCopy,
                onReplay = if (muted) null else ({ replays++ })
            )
            YodaFigure(talking || voicing || humming || speaking, caption, onTap = sayQuote)
        }
    }
}

/** Yoda himself. Tapping him plays one of his film lines, shown in [caption] while he says it. */
@Composable
private fun YodaFigure(talking: Boolean, caption: String?, onTap: () -> Unit) {
    val motion = rememberInfiniteTransition(label = "yoda")
    val breathe by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val nod by motion.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(240), RepeatMode.Reverse),
        label = "nod"
    )
    val talk by animateFloatAsState(if (talking) 1f else 0f, label = "talk")
    Box(contentAlignment = Alignment.BottomCenter) {
        Image(
            painter = painterResource(R.drawable.yoda),
            contentDescription = "Master Yoda. Tap him to hear him speak.",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .height(280.dp)
                .fillMaxWidth()
                .clickable(onClick = onTap)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    translationY = -breathe * 5.dp.toPx() + nod * talk * 2.dp.toPx()
                    scaleY = 1f + breathe * 0.012f
                    rotationZ = nod * talk * 1.2f
                }
        )
        if (caption != null) {
            Text(
                caption,
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = Color.White,
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, bottom = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun SpeechBubble(
    input: String,
    onInput: (String) -> Unit,
    editing: Boolean,
    focus: FocusRequester,
    focusOnEdit: Boolean,
    onEdit: () -> Unit,
    onSpeak: () -> Unit,
    spoken: String?,
    shown: Int,
    speakerLabel: String,
    thinking: Boolean,
    onCopy: () -> Unit,
    onReplay: (() -> Unit)?
) {
    LaunchedEffect(editing, focusOnEdit) {
        if (editing && focusOnEdit) focus.requestFocus()
    }
    // Each edit session opens with the cursor after the last word.
    var field by remember(editing) { mutableStateOf(TextFieldValue(input, TextRange(input.length))) }
    if (field.text != input) field = TextFieldValue(input, TextRange(input.length))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(BubbleFill)
                .then(if (editing) Modifier else Modifier.clickable(onClick = onEdit))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            if (editing) {
                Text("Say it to me, you will", style = MaterialTheme.typography.labelMedium, color = BubbleHint)
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = field,
                    // Enter speaks instead of starting a new line.
                    onValueChange = { typed ->
                        if ('\n' in typed.text) {
                            onInput(typed.text.replace("\n", ""))
                            onSpeak()
                        } else {
                            field = typed
                            onInput(typed.text)
                        }
                    },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 20.sp,
                        lineHeight = 28.sp,
                        color = BubbleInk
                    ),
                    cursorBrush = SolidColor(BubbleInk),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSpeak() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                    decorationBox = { field ->
                        Box {
                            if (input.isEmpty()) {
                                Text(
                                    "Du vil lære tålmodighet hvis du trener hardt hver dag.",
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 20.sp,
                                    lineHeight = 28.sp,
                                    color = BubbleInk.copy(alpha = 0.35f)
                                )
                            }
                            field()
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (input.isNotEmpty()) {
                        TextButton(onClick = { onInput("") }) {
                            Text("Clear", color = BubbleHint)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onSpeak,
                        enabled = input.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BubbleButton,
                            contentColor = Color.White
                        )
                    ) { Text("Speak") }
                }
            } else {
                Text(
                    "You: “${input.trim()}”",
                    style = MaterialTheme.typography.labelMedium,
                    color = BubbleHint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                val line = spoken.orEmpty()
                // The unrevealed tail is drawn transparent so the bubble never reflows.
                Text(
                    buildAnnotatedString {
                        append(line.take(shown))
                        withStyle(SpanStyle(color = Color.Transparent)) { append(line.drop(shown)) }
                    },
                    fontFamily = FontFamily.Serif,
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    color = BubbleInk
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (thinking) "Thinking, the model is…" else speakerLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = BubbleHint
                    )
                    Spacer(Modifier.weight(1f))
                    if (onReplay != null) {
                        IconButton(onClick = onReplay) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Hear it again",
                                tint = BubbleHint
                            )
                        }
                    }
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = BubbleHint)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = BubbleHint)
                    }
                }
            }
        }
        // The tail points down at Yoda.
        Canvas(Modifier.size(width = 28.dp, height = 16.dp)) {
            drawPath(
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width * 0.35f, size.height)
                    close()
                },
                BubbleFill
            )
        }
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
