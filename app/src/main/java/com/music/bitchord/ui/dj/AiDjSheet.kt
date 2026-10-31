package com.music.bitchord.ui.dj

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.dj.AiDjManager
import com.music.bitchord.ui.theme.SFProDisplay
import kotlinx.coroutines.CoroutineScope

// BitG Spotify DJ / Apple Music Minimalist Luxury Dark Palette
private val BitgSheetBackground = Color(0xFF0C0C0E)
private val BitgCardBackground = Color(0xFF141419)
private val BitgCardBorder = Color(0x1AFFFFFF)
private val BitgCyanAccent = Color(0xFF00D2FF)
private val BitgBlueGlow = Color(0xFF0A84FF)

@Composable
private fun MinimalistWaveform(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "WaveformTransition")
    val heights = (0 until 5).map { index ->
        val duration = if (isSpeaking) 260 + index * 50 else 850 + index * 100
        val minHeight = if (isSpeaking) 8f else 4f
        val maxHeight = if (isSpeaking) 26f else 10f
        val animatedHeight by transition.animateFloat(
            initialValue = minHeight,
            targetValue = maxHeight,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "WaveBar$index",
        )
        animatedHeight
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEach { barHeight ->
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(barHeight.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isSpeaking) BitgCyanAccent else Color.White.copy(alpha = 0.7f)),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDjSheet(
    controller: MediaController?,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isActive by AiDjManager.isActive.collectAsStateWithLifecycle()
    val isSpeaking by AiDjManager.isSpeaking.collectAsStateWithLifecycle()
    val currentVibe by AiDjManager.currentVibe.collectAsStateWithLifecycle()
    val speechText by AiDjManager.currentSpeech.collectAsStateWithLifecycle()

    var showBitgSettings by remember { mutableStateOf(false) }
    var showAiConfigDialog by remember { mutableStateOf(false) }
    var promptQuery by remember { mutableStateOf("") }

    val bitgAiProvider by AppSettings.bitgAiProvider.collectAsStateWithLifecycle()
    val bitgApiKey by AppSettings.bitgApiKey.collectAsStateWithLifecycle()
    val voicePitch by AppSettings.bitgVoicePitch.collectAsStateWithLifecycle()
    val voiceSpeed by AppSettings.bitgVoiceSpeed.collectAsStateWithLifecycle()
    val voiceEnabled by AppSettings.bitgVoiceEnabled.collectAsStateWithLifecycle()
    val voiceLang by AppSettings.bitgLanguage.collectAsStateWithLifecycle()
    val voiceName by AppSettings.bitgVoiceName.collectAsStateWithLifecycle()

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                promptQuery = spoken
                AiDjManager.askBitG(context, spoken, controller, scope)
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "BitGAuraPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isSpeaking) 1.12f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeaking) 400 else 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "BitGCoreScale",
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = if (isSpeaking) 0.50f else 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeaking) 450 else 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "BitGGlowAlpha",
    )

    if (showAiConfigDialog) {
        BitgAiConfigDialog(onDismiss = { showAiConfigDialog = false })
    }

    if (showBitgSettings) {
        AlertDialog(
            onDismissRequest = { showBitgSettings = false },
            containerColor = Color(0xFF141419),
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = stringResource(R.string.bitg_settings_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    // AI Brain config card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1A1A22),
                        border = BorderStroke(1.dp, BitgCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                showBitgSettings = false
                                showAiConfigDialog = true
                            }
                            .padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = BitgCyanAccent,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.bitg_ai_section_title),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = SFProDisplay,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = Color.White,
                                )
                                Text(
                                    text = if (bitgApiKey.isNotBlank()) {
                                        "Moteur : ${bitgAiProvider.uppercase()} • Connecté"
                                    } else {
                                        "Configurer clé IA (Gemini, Claude, Groq...)"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = SFProDisplay),
                                    color = Color.White.copy(alpha = 0.65f),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.bitg_voice_enabled),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = SFProDisplay,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = Color.White,
                            )
                            Text(
                                text = stringResource(R.string.bitg_voice_enabled_subtitle),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = SFProDisplay),
                                color = Color.White.copy(alpha = 0.6f),
                            )
                        }
                        Switch(
                            checked = voiceEnabled,
                            onCheckedChange = { AppSettings.setBitgVoiceEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BitgCyanAccent,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                uncheckedTrackColor = Color(0xFF262630),
                            ),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "${stringResource(R.string.bitg_voice_pitch)}: ${(voicePitch * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color.White,
                    )
                    Slider(
                        value = voicePitch,
                        onValueChange = { AppSettings.setBitgVoicePitch(it) },
                        valueRange = 0.5f..1.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = BitgCyanAccent,
                            activeTrackColor = BitgCyanAccent,
                            inactiveTrackColor = Color(0xFF262630),
                        ),
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = "${stringResource(R.string.bitg_voice_speed)}: ${(voiceSpeed * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color.White,
                    )
                    Slider(
                        value = voiceSpeed,
                        onValueChange = { AppSettings.setBitgVoiceSpeed(it) },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = BitgCyanAccent,
                            activeTrackColor = BitgCyanAccent,
                            inactiveTrackColor = Color(0xFF262630),
                        ),
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.bitg_language),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color.White,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("auto" to "Auto", "fr" to "Français", "en" to "English").forEach { (code, label) ->
                            val selected = voiceLang == code
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) BitgCyanAccent.copy(alpha = 0.2f) else Color(0xFF1E1E26),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) BitgCyanAccent else Color.Transparent,
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { AppSettings.setBitgLanguage(code) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) BitgCyanAccent else Color.White,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = SFProDisplay,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Sélection de la voix (Studio)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color.White,
                    )
                    Spacer(Modifier.height(8.dp))

                    val availableVoices = remember(voiceLang) { AiDjManager.getAvailableVoices() }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        availableVoices.forEach { voiceOption ->
                            val isSelected = (voiceName.isBlank() && voiceOption.id.isBlank()) || voiceName == voiceOption.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) BitgCyanAccent.copy(alpha = 0.15f) else Color(0xFF1A1A22),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) BitgCyanAccent.copy(alpha = 0.6f) else Color.Transparent,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        AppSettings.setBitgVoiceName(voiceOption.id)
                                        AiDjManager.applyTtsSettings()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = voiceOption.displayName,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = SFProDisplay,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        ),
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = BitgCyanAccent,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = { AiDjManager.testVoice() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BitgCyanAccent),
                        border = BorderStroke(1.dp, BitgCyanAccent.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Tester la voix de BitG",
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBitgSettings = false }) {
                    Text(
                        text = stringResource(R.string.done),
                        color = BitgCyanAccent,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = BitgSheetBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header Bar: Minimalist status pill & settings button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = BitgCardBackground,
                    border = BorderStroke(1.dp, BitgCardBorder),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isActive) BitgCyanAccent else Color.White.copy(alpha = 0.35f)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = currentVibe,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }

                IconButton(
                    onClick = { showBitgSettings = true },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(BitgCardBackground)
                        .border(1.dp, BitgCardBorder, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.bitg_settings_title),
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(17.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Central Spotify DJ Pulsing Fluid Aura Core
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(136.dp)
                    .clickable {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parle à BitG...")
                        }
                        runCatching { speechLauncher.launch(intent) }.onFailure {
                            Toast.makeText(context, "Reconnaissance vocale non disponible", Toast.LENGTH_SHORT).show()
                        }
                    },
            ) {
                // Soft Ambient Halo Glow
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    BitgBlueGlow.copy(alpha = glowAlpha),
                                    BitgCyanAccent.copy(alpha = glowAlpha * 0.4f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )

                // Sonic Ring Core
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF121217))
                        .border(
                            border = BorderStroke(
                                1.5.dp,
                                Brush.linearGradient(
                                    listOf(
                                        BitgCyanAccent.copy(alpha = 0.8f),
                                        BitgBlueGlow.copy(alpha = 0.4f),
                                    ),
                                ),
                            ),
                            shape = CircleShape,
                        ),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        MinimalistWaveform(isSpeaking = isSpeaking)
                        Spacer(Modifier.height(4.dp))
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isSpeaking) BitgCyanAccent else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.ai_dj_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                ),
                color = Color.White,
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = when {
                    isSpeaking -> "🎙️ BitG parle en direct..."
                    isActive -> "🎵 Session en cours • Touche pour lui parler"
                    else -> "Prêt à mixer • Touche le micro ou choisis une ambiance"
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = SFProDisplay),
                color = if (isSpeaking) BitgCyanAccent else Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )

            // Minimalist DJ Commentary Card (Clean, no AI slop banners)
            if (speechText.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = BitgCardBackground,
                    border = BorderStroke(1.dp, BitgCardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = if (isSpeaking) BitgCyanAccent else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "“ $speechText ”",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.Normal,
                            ),
                            color = Color.White.copy(alpha = 0.95f),
                            lineHeight = 21.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Quick Musical Vibe Chips
            val quickVibes = remember {
                listOf(
                    "🌟 Mes Classiques" to "Joue mes classiques et morceaux préférés",
                    "🔥 Gros Flow" to "Mets du gros rap avec des bonnes basses",
                    "🌊 Chill & Détente" to "Ambiance chill et calme pour se poser",
                    "⚡ Énergie Pure" to "Sons ultra énergiques et motivants",
                    "🎯 Focus & Travail" to "Musique pour se concentrer et travailler",
                    "🌙 Nocturne" to "Ambiance nocturne feutrée",
                    "📻 Découvertes" to "Fais-moi découvrir de nouveaux morceaux",
                    "🎸 Rock & Riffs" to "Du bon rock avec des riffs électriques",
                    "🕺 Club & Dance" to "Bangers électro pour danser",
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quickVibes.forEach { (label, prompt) ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = BitgCardBackground,
                        border = BorderStroke(1.dp, BitgCardBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                AiDjManager.askBitG(context, prompt, controller, scope)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Sleek Minimalist Prompt Input Bar
            OutlinedTextField(
                value = promptQuery,
                onValueChange = { promptQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Demander une ambiance, un artiste, un titre...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SFProDisplay),
                        color = Color.White.copy(alpha = 0.4f),
                    )
                },
                leadingIcon = {
                    IconButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Parle à BitG...")
                            }
                            runCatching { speechLauncher.launch(intent) }.onFailure {
                                Toast.makeText(context, "Reconnaissance vocale non disponible", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = stringResource(R.string.bitg_prompt_speak),
                            tint = BitgCyanAccent,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                },
                trailingIcon = {
                    if (promptQuery.isNotBlank()) {
                        IconButton(
                            onClick = {
                                keyboardController?.hide()
                                val query = promptQuery
                                promptQuery = ""
                                AiDjManager.askBitG(context, query, controller, scope)
                            },
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(BitgCyanAccent),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = stringResource(R.string.bitg_prompt_send),
                                    tint = Color.Black,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = BitgCardBackground,
                    unfocusedContainerColor = BitgCardBackground,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = BitgCyanAccent.copy(alpha = 0.8f),
                    unfocusedBorderColor = BitgCardBorder,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (promptQuery.isNotBlank()) {
                            keyboardController?.hide()
                            val query = promptQuery
                            promptQuery = ""
                            AiDjManager.askBitG(context, query, controller, scope)
                        }
                    },
                ),
            )

            Spacer(Modifier.height(16.dp))

            // Action Buttons: Sleek & Understated
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        AiDjManager.switchVibe(context, controller, scope)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1B1B22),
                        contentColor = Color.White,
                    ),
                    border = BorderStroke(1.dp, BitgCyanAccent.copy(alpha = 0.4f)),
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ai_dj_switch_vibe),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }

                if (isActive) {
                    Button(
                        onClick = {
                            AiDjManager.stop(controller, pauseMusic = false)
                        },
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF221618),
                            contentColor = Color(0xFFFF453A),
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFF453A).copy(alpha = 0.3f)),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Arrêter",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}
