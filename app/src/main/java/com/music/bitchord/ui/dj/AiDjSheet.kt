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

// BitG Luxury Cyber Palette
private val BitgViolet = Color(0xFF8A2BE2)
private val BitgNeonCyan = Color(0xFF00D2FF)
private val BitgCrimson = Color(0xFFFA2D48)
private val BitgSheetBackground = Color(0xFF0C0C10)
private val BitgGlassSurface = Color(0xFF161620)
private val BitgGlassBorder = Color(0x25FFFFFF)

@Composable
private fun EqualizerWave(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "EqualizerWaveTransition")
    val heights = (0 until 5).map { index ->
        val duration = if (isSpeaking) 320 + index * 70 else 1000 + index * 160
        val minHeight = if (isSpeaking) 10f else 6f
        val maxHeight = if (isSpeaking) 36f else 18f
        val animatedHeight by transition.animateFloat(
            initialValue = minHeight,
            targetValue = maxHeight,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "EqualizerBar$index",
        )
        animatedHeight
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEachIndexed { idx, barHeight ->
            val barBrush = when (idx) {
                0 -> Brush.verticalGradient(listOf(BitgNeonCyan, BitgViolet))
                1 -> Brush.verticalGradient(listOf(BitgViolet, BitgCrimson))
                2 -> Brush.verticalGradient(listOf(BitgCrimson, BitgViolet))
                3 -> Brush.verticalGradient(listOf(BitgViolet, BitgNeonCyan))
                else -> Brush.verticalGradient(listOf(BitgNeonCyan, BitgViolet))
            }
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(barHeight.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barBrush),
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

    val infiniteTransition = rememberInfiniteTransition(label = "BitGSonicHaloPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isSpeaking) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeaking) 450 else 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "BitGCoreScale",
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = if (isSpeaking) 0.55f else 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeaking) 500 else 1600, easing = FastOutSlowInEasing),
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
            containerColor = Color(0xFF14141C),
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
                        color = Color(0xFF1F172E),
                        border = BorderStroke(1.dp, BitgViolet.copy(alpha = 0.7f)),
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
                                tint = BitgNeonCyan,
                                modifier = Modifier.size(24.dp),
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
                                        "Fournisseur : ${bitgAiProvider.uppercase()} • Clé API active"
                                    } else {
                                        "Activer Gemini, Groq, OpenAI... (Clé API)"
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
                                checkedTrackColor = BitgViolet,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                uncheckedTrackColor = Color(0xFF2C2C38),
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
                            thumbColor = BitgNeonCyan,
                            activeTrackColor = BitgViolet,
                            inactiveTrackColor = Color(0xFF2C2C38),
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
                            thumbColor = BitgNeonCyan,
                            activeTrackColor = BitgViolet,
                            inactiveTrackColor = Color(0xFF2C2C38),
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
                                color = if (selected) BitgViolet else Color(0xFF20202C),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) BitgNeonCyan.copy(alpha = 0.6f) else Color.Transparent,
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { AppSettings.setBitgLanguage(code) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
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
                        text = "Sélection de la voix (Studio HD)",
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
                                color = if (isSelected) BitgViolet.copy(alpha = 0.5f) else Color(0xFF20202C),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) BitgNeonCyan.copy(alpha = 0.8f) else Color.Transparent,
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
                                            tint = BitgNeonCyan,
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BitgNeonCyan),
                        border = BorderStroke(1.dp, BitgNeonCyan.copy(alpha = 0.6f)),
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
                        color = BitgNeonCyan,
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
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header: Dynamic Pill Status Badge & Glass Settings Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = BitgGlassSurface,
                    border = BorderStroke(1.dp, BitgGlassBorder),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isActive) BitgNeonCyan else Color.White.copy(alpha = 0.4f)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = currentVibe,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = Color.White,
                        )
                    }
                }

                IconButton(
                    onClick = { showBitgSettings = true },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(BitgGlassSurface)
                        .border(1.dp, BitgGlassBorder, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.bitg_settings_title),
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // AI Status & Configuration Badge
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (bitgApiKey.isNotBlank()) Color(0xFF1E1430) else Color(0xFF221A10),
                border = BorderStroke(
                    1.dp,
                    if (bitgApiKey.isNotBlank()) BitgViolet.copy(alpha = 0.8f) else Color(0xFFFFB300).copy(alpha = 0.6f),
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { showAiConfigDialog = true }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = if (bitgApiKey.isNotBlank()) BitgNeonCyan else Color(0xFFFFB300),
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = if (bitgApiKey.isNotBlank()) {
                            "IA BitG : ${bitgAiProvider.uppercase()} (Apprentissage actif)"
                        } else {
                            "⚡ Activer la vraie IA (Clé API)"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color.White,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Futuristic Sonic Core Visualizer
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp),
            ) {
                // Soft Ambient Halo Glow
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    BitgViolet.copy(alpha = glowAlpha),
                                    BitgNeonCyan.copy(alpha = glowAlpha * 0.6f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )

                // Sonic Ring Core
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF12121A))
                        .border(
                            border = BorderStroke(
                                2.dp,
                                Brush.sweepGradient(
                                    listOf(
                                        BitgViolet,
                                        BitgNeonCyan,
                                        BitgCrimson,
                                        BitgViolet,
                                    ),
                                ),
                            ),
                            shape = CircleShape,
                        )
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        EqualizerWave(isSpeaking = isSpeaking)
                        Spacer(Modifier.height(6.dp))
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isSpeaking) BitgNeonCyan else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.ai_dj_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                ),
                color = Color.White,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = when {
                    isSpeaking -> "🎙️ BitG parle en direct..."
                    isActive -> "🎵 Session en cours • Appuie pour lui parler"
                    else -> "Prêt à mixer • Demande-lui un artiste ou une vibe"
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = SFProDisplay),
                color = if (isSpeaking) BitgNeonCyan else Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
            )

            // Dynamic Glass Speech Bubble
            if (speechText.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF161622),
                    border = BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                BitgViolet.copy(alpha = if (isSpeaking) 0.7f else 0.25f),
                                BitgNeonCyan.copy(alpha = if (isSpeaking) 0.5f else 0.2f),
                            ),
                        ),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = if (isSpeaking) BitgNeonCyan else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = speechText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = Color.White,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Futuristic Floating Prompt Input Bar
            OutlinedTextField(
                value = promptQuery,
                onValueChange = { promptQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Demande un artiste, un titre, un style...",
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
                            tint = BitgNeonCyan,
                            modifier = Modifier.size(20.dp),
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
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(BitgViolet, BitgNeonCyan),
                                        ),
                                    ),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = stringResource(R.string.bitg_prompt_send),
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = BitgGlassSurface,
                    unfocusedContainerColor = BitgGlassSurface,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = BitgViolet,
                    unfocusedBorderColor = BitgGlassBorder,
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

            Spacer(Modifier.height(12.dp))

            // Curated Suggestion Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "🔥 Vald",
                    "⚡ Drill FR",
                    "🌙 Lo-Fi Chill",
                    "🎸 Rock 80s",
                    "✨ Daft Punk",
                    "🏖️ Deep House",
                    "💎 Pépites Cachées",
                ).forEach { suggestion ->
                    val cleanLabel = suggestion.substringAfter(" ")
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF191924),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                keyboardController?.hide()
                                AiDjManager.askBitG(context, cleanLabel, controller, scope)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Primary Action Button: "Changer d'ambiance" with BitChard Signature Cyber Gradient
            Button(
                onClick = {
                    AiDjManager.switchVibe(context, controller, scope)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF7B2CBF),
                                Color(0xFF9D4EDD),
                                BitgCrimson,
                            ),
                        ),
                    ),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.ai_dj_switch_vibe),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            Spacer(Modifier.height(10.dp))

            // Secondary Action Button: Stop DJ Session (Glass finish)
            OutlinedButton(
                onClick = {
                    AiDjManager.stop(controller, pauseMusic = false)
                    Toast.makeText(context, "Session BitG terminée", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, BitgCrimson.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFF181216),
                    contentColor = BitgCrimson,
                ),
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.ai_dj_stop),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}
