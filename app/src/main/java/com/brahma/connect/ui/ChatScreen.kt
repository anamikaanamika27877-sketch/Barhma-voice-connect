package com.brahma.connect.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brahma.connect.core.AgentStateStore
import com.brahma.connect.core.ChatMessage
import com.brahma.connect.core.VoiceState
import com.brahma.connect.voice.BrahmaVoiceManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    val chatHistory by AgentStateStore.chatHistory.collectAsState()
    val voiceState by AgentStateStore.voiceState.collectAsState()
    val liveSpokenText by AgentStateStore.liveSpokenText.collectAsState()
    val statusText by AgentStateStore.statusText.collectAsState()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom on new message
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    // Set voice result callback
    LaunchedEffect(Unit) {
        BrahmaVoiceManager.setSpeechResultListener { text ->
            if (text.isNotBlank()) {
                onSendMessage(text)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "BRAHMA VOICE CONSOLE",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF),
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "Hands-free Voice Only • 'Hey Brahma'",
                            fontSize = 11.sp,
                            color = Color(0xFF80D8FF)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0x99050A14)
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Chat history list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (chatHistory.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Say 'Hey Brahma' or tap the microphone\nto speak your command.",
                                color = Color(0x88FFFFFF),
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 20.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                items(chatHistory, key = { it.id }) { message ->
                    ChatMessageBubble(message = message)
                }
            }

            // Live speech transcription preview (if actively speaking)
            if (liveSpokenText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x3300E5FF))
                        .border(1.dp, Color(0x6600E5FF), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Heard: ",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = liveSpokenText,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // PURE VOICE CONTROL CONSOLE (NO TEXT INPUT OPTION)
            VoiceControlDock(
                voiceState = voiceState,
                statusText = statusText,
                onMicClick = {
                    if (voiceState == VoiceState.LISTENING) {
                        BrahmaVoiceManager.stopListening()
                    } else {
                        BrahmaVoiceManager.startListening { text ->
                            onSendMessage(text)
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun VoiceControlDock(
    voiceState: VoiceState,
    statusText: String,
    onMicClick: () -> Unit
) {
    // Pulse animation when listening or speaking
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (voiceState == VoiceState.LISTENING || voiceState == VoiceState.SPEAKING) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val orbColor = when (voiceState) {
        VoiceState.LISTENING -> Color(0xFF00E5FF)
        VoiceState.SPEAKING -> Color(0xFF76FF03)
        VoiceState.PROCESSING -> Color(0xFFFFD600)
        VoiceState.IDLE -> Color(0xFF00B0FF)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(0xEE0A0F1D))
            .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(vertical = 16.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // State label
            Text(
                text = when (voiceState) {
                    VoiceState.LISTENING -> "● LISTENING..."
                    VoiceState.SPEAKING -> "▶ BRAHMA SPEAKING..."
                    VoiceState.PROCESSING -> "◌ PROCESSING..."
                    VoiceState.IDLE -> "TAP MIC OR SAY 'HEY BRAHMA'"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = orbColor
            )

            // Central Pulsing Voice Orb Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(80.dp)
            ) {
                // Outer glow halo
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(orbColor.copy(alpha = 0.25f))
                )

                // Main circular mic button
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(orbColor, Color(0xFF003846))
                            )
                        )
                        .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                        .clickable { onMicClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (voiceState) {
                            VoiceState.SPEAKING -> Icons.Default.VolumeUp
                            VoiceState.LISTENING -> Icons.Default.Mic
                            else -> Icons.Default.MicNone
                        },
                        contentDescription = "Voice Input",
                        tint = Color.Black,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // Subtitle status
            Text(
                text = statusText,
                fontSize = 11.sp,
                color = Color(0x99FFFFFF),
                maxLines = 1
            )
        }
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessage) {
    val isUser = message.role.lowercase() == "user"
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) {
                        Brush.linearGradient(listOf(Color(0xFF005C6E), Color(0xFF003846)))
                    } else {
                        Brush.linearGradient(listOf(Color(0xFF141C2B), Color(0xFF1E283D)))
                    }
                )
                .border(
                    1.dp,
                    if (isUser) Color(0x6600E5FF) else Color(0x3300E5FF),
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 16.dp
                    )
                )
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "YOU (VOICE)" else "BRAHMA AI",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isUser) Color(0xFF00E5FF) else Color(0xFF70E0FF)
                    )
                    Text(
                        text = formattedTime,
                        fontSize = 9.sp,
                        color = Color(0x66FFFFFF)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}
