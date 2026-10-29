package com.brahma.connect.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.provider.Settings
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.brahma.connect.accessibility.BrahmaAccessibilityService
import com.brahma.connect.core.AgentStateStore
import com.brahma.connect.core.ConnectionState
import com.brahma.connect.core.GatewayEndpoint
import com.brahma.connect.core.PairingOffer
import com.brahma.connect.pairing.PairingPayloadParser
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

enum class AppScreen {
    WELCOME,
    PERMISSIONS,
    CHOICE,
    DISCOVERY,
    QR_SCAN,
    PAIRING_PENDING,
    CONNECTED,
    VOICE_CHAT
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HolographicBackground() {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(0) // Transparent
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }
                loadUrl("file:///android_asset/web_background/index.html")
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xCC0A101D)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content
        )
    }
}

@Composable
fun BrahmaConnectApp(
    onRequestCameraPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestMicrophonePermission: () -> Unit,
    onStartService: () -> Unit,
    onConnectGateway: (GatewayEndpoint) -> Unit,
    onSendPairRequest: (PairingOffer) -> Unit,
    onDisconnect: () -> Unit,
    onSendVoiceMessage: (String) -> Unit
) {
    val connectionState by AgentStateStore.connectionState.collectAsState()
    val gateway by AgentStateStore.gateway.collectAsState()
    val pairingOffer by AgentStateStore.pairingOffer.collectAsState()
    val credential by AgentStateStore.credential.collectAsState()
    val lastError by AgentStateStore.lastError.collectAsState()
    val statusText by AgentStateStore.statusText.collectAsState()
    val logs by AgentStateStore.logs.collectAsState()

    var currentScreen by remember { mutableStateOf(AppScreen.WELCOME) }
    var showManualDialog by remember { mutableStateOf(false) }
    var manualHost by remember { mutableStateOf("192.168.1.") }
    var manualPort by remember { mutableStateOf("8765") }

    // If already paired & connected, switch automatically
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED && currentScreen != AppScreen.VOICE_CHAT) {
            currentScreen = AppScreen.CONNECTED
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Holographic 3D Background with Three.js
        HolographicBackground()

        // Screen Routing
        when (currentScreen) {
            AppScreen.WELCOME -> {
                WelcomeScreen(
                    onNext = { currentScreen = AppScreen.PERMISSIONS }
                )
            }
            AppScreen.PERMISSIONS -> {
                StartupPermissionsScreen(
                    onRequestCamera = onRequestCameraPermission,
                    onRequestNotification = onRequestNotificationPermission,
                    onRequestMicrophone = onRequestMicrophonePermission,
                    onContinue = {
                        onStartService()
                        currentScreen = AppScreen.CHOICE
                    }
                )
            }
            AppScreen.CHOICE -> {
                ConnectChoiceScreen(
                    onScanQr = { currentScreen = AppScreen.QR_SCAN },
                    onEnterIp = { showManualDialog = true },
                    onFindLocal = { currentScreen = AppScreen.DISCOVERY }
                )
            }
            AppScreen.DISCOVERY -> {
                DiscoveryScreen(
                    gateway = gateway,
                    status = statusText,
                    error = lastError,
                    onConnect = {
                        gateway?.let { onConnectGateway(it) }
                    },
                    onFindBrahma = { /* triggers nsd */ },
                    onBack = { currentScreen = AppScreen.CHOICE }
                )
            }
            AppScreen.QR_SCAN -> {
                QrScannerScreen(
                    onBack = { currentScreen = AppScreen.CHOICE },
                    onRequestPermission = onRequestCameraPermission,
                    errorText = lastError,
                    onScanned = { offer ->
                        AgentStateStore.setPairingOffer(offer)
                        val ep = GatewayEndpoint(
                            name = offer.service,
                            host = offer.host,
                            port = offer.port,
                            online = true
                        )
                        onConnectGateway(ep)
                        currentScreen = AppScreen.PAIRING_PENDING
                    }
                )
            }
            AppScreen.PAIRING_PENDING -> {
                PairingPendingScreen(
                    offer = pairingOffer,
                    gateway = gateway,
                    state = connectionState,
                    onRequestPairing = {
                        pairingOffer?.let { onSendPairRequest(it) }
                    },
                    onCancel = {
                        currentScreen = AppScreen.CHOICE
                    }
                )
            }
            AppScreen.CONNECTED -> {
                ConnectedScreen(
                    credential = credential,
                    gateway = gateway,
                    state = connectionState,
                    logs = logs,
                    onOpenVoiceConsole = { currentScreen = AppScreen.VOICE_CHAT },
                    onDisconnect = onDisconnect
                )
            }
            AppScreen.VOICE_CHAT -> {
                ChatScreen(
                    onBack = { currentScreen = AppScreen.CONNECTED },
                    onSendMessage = onSendVoiceMessage
                )
            }
        }

        if (showManualDialog) {
            ManualConnectDialog(
                host = manualHost,
                port = manualPort,
                onHostChange = { manualHost = it },
                onPortChange = { manualPort = it },
                onDismiss = { showManualDialog = false },
                onConnect = {
                    showManualDialog = false
                    val ep = GatewayEndpoint(
                        name = "Manual Brahma PC",
                        host = manualHost.trim(),
                        port = manualPort.toIntOrNull() ?: 8765,
                        online = true
                    )
                    onConnectGateway(ep)
                    currentScreen = AppScreen.PAIRING_PENDING
                }
            )
        }
    }
}

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard {
            Text(
                "BRAHMA CONNECT",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF),
                fontFamily = FontFamily.Monospace
            )
            Text(
                "VOICE EDITION",
                fontSize = 14.sp,
                color = Color(0xFF80D8FF),
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Experience completely hands-free control of your phone with Brahma AI. No typing needed — speak naturally and wake up the system anytime with 'Hey Brahma'.",
                color = Color(0xCCFFFFFF),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("GET STARTED", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StartupPermissionsScreen(
    onRequestCamera: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val hasMic = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == 0
    val hasCamera = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard {
            Text(
                "SETUP REQUIRED",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF),
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Enable the permissions Brahma Connect needs to listen to your voice and control your phone reliably.",
                color = Color(0xCCFFFFFF),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(20.dp))

            PermissionRow(
                label = "Microphone (Voice Control & Wake-Word)",
                granted = hasMic,
                onClick = onRequestMicrophone
            )
            Spacer(modifier = Modifier.height(10.dp))

            PermissionRow(
                label = "Camera (QR Scanner)",
                granted = hasCamera,
                onClick = onRequestCamera
            )
            Spacer(modifier = Modifier.height(10.dp))

            PermissionRow(
                label = "Accessibility Service",
                granted = BrahmaAccessibilityService.instance != null,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("CONTINUE", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PermissionRow(label: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x22FFFFFF))
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            text = if (granted) "Granted" else "Grant",
            color = if (granted) Color(0xFF76FF03) else Color(0xFFFFD600),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
fun ConnectChoiceScreen(
    onScanQr: () -> Unit,
    onEnterIp: () -> Unit,
    onFindLocal: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard {
            Text(
                "CONNECT TO BRAHMA",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF),
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Choose how to link this phone with your Brahma AI PC.",
                color = Color(0xCCFFFFFF),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onScanQr,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SCAN QR CODE", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onFindLocal,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("FIND LOCAL PC (mDNS)", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onEnterIp,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ENTER IP ADDRESS", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DiscoveryScreen(
    gateway: GatewayEndpoint?,
    status: String,
    error: String?,
    onConnect: () -> Unit,
    onFindBrahma: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "DISCOVERY",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF),
                    fontFamily = FontFamily.Monospace
                )
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(status, color = Color.White, fontSize = 14.sp)
            if (error != null) {
                Text(error, color = Color(0xFFFF5252), fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
            if (gateway != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x3300E5FF))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(gateway.name, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${gateway.host}:${gateway.port}", color = Color(0xFF80D8FF), fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CONNECT NOW", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            } else {
                CircularProgressIndicator(
                    color = Color(0xFF00E5FF),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
fun QrScannerScreen(
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    errorText: String?,
    onScanned: (PairingOffer) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == 0)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val scanner = BarcodeScanning.getClient()
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        for (barcode in barcodes) {
                                            val raw = barcode.rawValue ?: continue
                                            val offer = PairingPayloadParser.parse(raw)
                                            if (offer != null) {
                                                onScanned(offer)
                                                break
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else {
                                imageProxy.close()
                            }
                        }

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Overlay & Controls
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x99000000))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC000000))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Point your camera at the Brahma PC pairing QR code",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun PairingPendingScreen(
    offer: PairingOffer?,
    gateway: GatewayEndpoint?,
    state: ConnectionState,
    onRequestPairing: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard {
            Text(
                "CONNECT TO BRAHMA?",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF),
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("Host: ${gateway?.host ?: offer?.host}", color = Color.White)
            Text("Service: ${offer?.service ?: "Brahma PC"}", color = Color(0xFF80D8FF))
            Text("Pairing Code: ${offer?.pairingCode}", color = Color(0xFF76FF03), fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRequestPairing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("APPROVE & LINK", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", color = Color(0xAAFFFFFF))
            }
        }
    }
}

@Composable
fun ConnectedScreen(
    credential: com.brahma.connect.core.DeviceCredential?,
    gateway: GatewayEndpoint?,
    state: ConnectionState,
    logs: List<String>,
    onOpenVoiceConsole: () -> Unit,
    onDisconnect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "BRAHMA CONNECT",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "● Connected & Active",
                        fontSize = 12.sp,
                        color = Color(0xFF76FF03)
                    )
                }
                IconButton(onClick = onDisconnect) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = "Disconnect", tint = Color(0xFFFF5252))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Linked to: ${gateway?.name ?: credential?.gatewayHost}", color = Color.White, fontSize = 13.sp)
            Text("Wake Word: 'Hey Brahma' (Active in background)", color = Color(0xFF00E5FF), fontSize = 12.sp)

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onOpenVoiceConsole,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("OPEN VOICE CONSOLE", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Recent Logs", color = Color(0x88FFFFFF), fontSize = 11.sp, fontFamily = FontFamily.Monospace)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x33000000))
                    .padding(8.dp)
            ) {
                items(logs.takeLast(10)) { log ->
                    Text(
                        text = "• $log",
                        color = Color(0xBBFFFFFF),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun ManualConnectDialog(
    host: String,
    port: String,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConnect: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual Connection", color = Color(0xFF00E5FF)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = onHostChange,
                    label = { Text("Gateway Host IP") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF)
                    )
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = onPortChange,
                    label = { Text("Port (default 8765)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConnect,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
            ) {
                Text("Connect", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }
        },
        containerColor = Color(0xFF0D1424)
    )
}
