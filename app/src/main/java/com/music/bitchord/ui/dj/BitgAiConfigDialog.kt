package com.music.bitchord.ui.dj

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.dj.BitgAiService
import com.music.bitchord.ui.theme.SFProDisplay
import kotlinx.coroutines.launch

private val BitgViolet = Color(0xFF8A2BE2)
private val BitgNeonCyan = Color(0xFF00D2FF)
private val BitgSurface = Color(0xFF151520)
private val BitgBorder = Color(0x30FFFFFF)

data class ProviderOption(
    val id: String,
    val name: String,
    val subtitle: String,
)

val PROVIDER_OPTIONS = listOf(
    ProviderOption("gemini", "Gemini", "Gratuit & Recommandé"),
    ProviderOption("groq", "Groq", "Llama 3 ultra-rapide"),
    ProviderOption("openai", "OpenAI", "GPT-4o mini"),
    ProviderOption("claude", "Claude", "Anthropic Claude 3.5"),
    ProviderOption("custom", "Custom", "OpenRouter / Local"),
)

@Composable
fun BitgAiConfigDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val currentProvider by AppSettings.bitgAiProvider.collectAsStateWithLifecycle()
    val savedApiKey by AppSettings.bitgApiKey.collectAsStateWithLifecycle()
    val savedCustomModel by AppSettings.bitgCustomModel.collectAsStateWithLifecycle()
    val savedCustomEndpoint by AppSettings.bitgCustomEndpoint.collectAsStateWithLifecycle()

    var selectedProvider by remember(currentProvider) { mutableStateOf(currentProvider) }
    var apiKeyText by remember(savedApiKey) { mutableStateOf(savedApiKey) }
    var customModelText by remember(savedCustomModel) { mutableStateOf(savedCustomModel) }
    var customEndpointText by remember(savedCustomEndpoint) { mutableStateOf(savedCustomEndpoint) }

    var passwordVisible by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF13131B),
        shape = RoundedCornerShape(26.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = BitgNeonCyan,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.bitg_ai_section_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color.White,
                    )
                    Text(
                        text = "Apprentissage & affinités musicales",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = SFProDisplay),
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.bitg_ai_desc),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = SFProDisplay),
                    color = Color.White.copy(alpha = 0.75f),
                    lineHeight = 18.sp,
                )

                Spacer(Modifier.height(16.dp))

                // Provider selection chips
                Text(
                    text = stringResource(R.string.bitg_ai_provider),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PROVIDER_OPTIONS.forEach { opt ->
                        val isSelected = selectedProvider == opt.id
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) BitgViolet else Color(0xFF1E1E2A),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) BitgNeonCyan.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f),
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    selectedProvider = opt.id
                                    testResult = null
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        ) {
                            Column {
                                Text(
                                    text = opt.name,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = SFProDisplay,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                                Text(
                                    text = opt.subtitle,
                                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = SFProDisplay),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // API Key input field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.bitg_api_key),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color.White,
                    )
                    // Quick paste button
                    TextButton(
                        onClick = {
                            clipboardManager.getText()?.text?.let { clipboardText ->
                                if (clipboardText.isNotBlank()) {
                                    apiKeyText = clipboardText.trim()
                                    testResult = null
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentPaste,
                            contentDescription = null,
                            tint = BitgNeonCyan,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Coller",
                            color = BitgNeonCyan,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }

                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = {
                        apiKeyText = it
                        testResult = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = when (selectedProvider) {
                                "gemini" -> "AIzaSy..."
                                "groq" -> "gsk_..."
                                "openai" -> "sk-..."
                                "claude" -> "sk-ant-..."
                                else -> "Clé d'API"
                            },
                            color = Color.White.copy(alpha = 0.35f),
                            fontFamily = SFProDisplay,
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (apiKeyText.isNotBlank()) {
                                IconButton(onClick = { apiKeyText = ""; testResult = null }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "Effacer",
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (passwordVisible) "Masquer" else "Afficher",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BitgSurface,
                        unfocusedContainerColor = BitgSurface,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BitgNeonCyan,
                        unfocusedBorderColor = BitgBorder,
                    ),
                )

                // Optional custom provider fields
                if (selectedProvider == "custom") {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.bitg_custom_endpoint),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = SFProDisplay),
                        color = Color.White,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customEndpointText,
                        onValueChange = { customEndpointText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "https://openrouter.ai/api/v1",
                                color = Color.White.copy(alpha = 0.35f),
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = BitgSurface,
                            unfocusedContainerColor = BitgSurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = BitgNeonCyan,
                            unfocusedBorderColor = BitgBorder,
                        ),
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.bitg_custom_model),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = SFProDisplay),
                        color = Color.White,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customModelText,
                        onValueChange = { customModelText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "meta-llama/llama-3.1-70b-instruct",
                                color = Color.White.copy(alpha = 0.35f),
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = BitgSurface,
                            unfocusedContainerColor = BitgSurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = BitgNeonCyan,
                            unfocusedBorderColor = BitgBorder,
                        ),
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Test connection button & feedback
                OutlinedButton(
                    onClick = {
                        // Persist current draft first so testConnection reads it
                        AppSettings.setBitgAiProvider(selectedProvider)
                        AppSettings.setBitgApiKey(apiKeyText.trim())
                        AppSettings.setBitgCustomEndpoint(customEndpointText.trim())
                        AppSettings.setBitgCustomModel(customModelText.trim())

                        isTesting = true
                        testResult = null
                        coroutineScope.launch {
                            val res = BitgAiService.testConnection(context)
                            isTesting = false
                            res.fold(
                                onSuccess = { msg ->
                                    testSuccess = true
                                    testResult = msg
                                },
                                onFailure = { err ->
                                    testSuccess = false
                                    testResult = err.message ?: "Erreur de connexion inconnue"
                                },
                            )
                        }
                    },
                    enabled = !isTesting && apiKeyText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (testSuccess) Color(0xFF00E676) else BitgNeonCyan.copy(alpha = 0.6f)),
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = BitgNeonCyan,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.bitg_ai_testing),
                            color = Color.White,
                            fontFamily = SFProDisplay,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.bitg_ai_test),
                            color = BitgNeonCyan,
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                AnimatedVisibility(visible = testResult != null) {
                    testResult?.let { msg ->
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (testSuccess) Color(0x2000E676) else Color(0x20FF5252),
                            border = BorderStroke(1.dp, if (testSuccess) Color(0x6000E676) else Color(0x60FF5252)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (testSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (testSuccess) Color(0xFF00E676) else Color(0xFFFF5252),
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = SFProDisplay),
                                    color = Color.White,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    AppSettings.setBitgAiProvider(selectedProvider)
                    AppSettings.setBitgApiKey(apiKeyText.trim())
                    AppSettings.setBitgCustomEndpoint(customEndpointText.trim())
                    AppSettings.setBitgCustomModel(customModelText.trim())
                    Toast.makeText(context, "Paramètres BitG IA enregistrés", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BitgViolet,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = "Enregistrer",
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = Color.White.copy(alpha = 0.65f),
                    fontFamily = SFProDisplay,
                )
            }
        },
    )
}
