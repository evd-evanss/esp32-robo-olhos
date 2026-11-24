package com.evandro.roboolhos.features.companion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evandro.roboolhos.core.designsystem.Mint
import com.evandro.roboolhos.core.robot.LinkPhase

private val DeepNavy = Color(0xFF071019)
private val Glass = Color(0xCC142330)
private val SoftMint = Color(0xFF9BF7D2)
private val Violet = Color(0xFF9B8CFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionScreen(viewModel: CompanionViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF102738), DeepNavy, Color(0xFF050B11))))) {
        Scaffold(containerColor = Color.Transparent, topBar = { AppHeader(state, viewModel::dispatch) }) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { RobotHero(state) }
                if (state.link.phase != LinkPhase.READY) item { ConnectionPanel(state, viewModel::dispatch) }
                else {
                    item { QuickActions(state.busy, viewModel::dispatch) }
                    item { ConversationPanel(state, viewModel::dispatch) }
                    if (state.lastUserText.isNotBlank()) item { CorrectionPanel(viewModel::dispatch) }
                    item { TrainingPanel(state, viewModel::dispatch) }
                }
            }
        }
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.dispatch(CompanionIntent.DismissError) },
            icon = { Icon(Icons.Default.ErrorOutline, null) },
            title = { Text("Algo não saiu como esperado") }, text = { Text(error) },
            confirmButton = { TextButton({ viewModel.dispatch(CompanionIntent.DismissError) }) { Text("Entendi") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppHeader(state: CompanionState, dispatch: (CompanionIntent) -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = { Column { Text("ROBÔ OLHOS", style = MaterialTheme.typography.labelSmall, color = Mint); Text("Companheiro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) } },
        actions = {
            ConnectionDot(state.link.phase); Spacer(Modifier.width(8.dp))
            IconButton({ dispatch(CompanionIntent.ToggleSound) }, Modifier.background(Color.White.copy(alpha = .07f), CircleShape)) {
                Icon(if (state.soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, if (state.soundEnabled) "Desativar voz" else "Ativar voz")
            }
            Spacer(Modifier.width(8.dp))
        },
    )
}

@Composable
private fun ConnectionDot(phase: LinkPhase) {
    val connected = phase == LinkPhase.READY
    Surface(color = Color.White.copy(alpha = .07f), shape = CircleShape) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(if (connected) Mint else Color(0xFFFFC46B), CircleShape)); Spacer(Modifier.width(6.dp))
            Text(if (connected) "Online" else "Offline", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun RobotHero(state: CompanionState) {
    Surface(shape = RoundedCornerShape(30.dp), color = Glass, border = BorderStroke(1.dp, Color.White.copy(alpha = .09f)), shadowElevation = 12.dp) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(if (state.localAiAvailable) Icons.Default.AutoAwesome else Icons.Default.Memory, if (state.localAiAvailable) "IA local" else "ESP32 local", if (state.localAiAvailable) Violet else Mint)
                Text(state.robot.interaction.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
            RobotEyes(state.robot.face)
            Text(state.robot.face.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(if (state.link.phase == LinkPhase.READY) state.link.deviceName.ifBlank { "Robô conectado" } else state.link.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun StatusBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, accent: Color) {
    Surface(color = accent.copy(alpha = .13f), shape = CircleShape, border = BorderStroke(1.dp, accent.copy(alpha = .25f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)); Text(text, style = MaterialTheme.typography.labelMedium, color = accent)
        }
    }
}

@Composable
private fun RobotEyes(face: String) {
    val transition = rememberInfiniteTransition(label = "vida")
    val blink by transition.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(keyframes { durationMillis = 4200; 1f at 0; 1f at 3500; .08f at 3580; 1f at 3700; 1f at 4200 }, RepeatMode.Restart),
        label = "piscar",
    )
    Canvas(Modifier.fillMaxWidth().height(128.dp).semantics { contentDescription = "Expressão $face" }) {
        listOf(size.width * .34f, size.width * .66f).forEachIndexed { index, center ->
            if (face == "feliz" && blink > .45f) {
                drawArc(SoftMint, 200f, 140f, false, Offset(center - 32f, size.height / 2 - 26f), Size(64f, 54f), style = Stroke(15f, cap = StrokeCap.Round))
            } else {
                val base = when (face) { "sono" -> 7f; "surpresa" -> 66f; "raiva" -> 42f; else -> 54f }
                val height = ((base * blink).coerceAtLeast(5f) * if (face == "curioso" && index == 1) .62f else 1f)
                val angle = when (face) { "raiva" -> if (index == 0) 13f else -13f; "triste" -> if (index == 0) -11f else 11f; else -> 0f }
                rotate(angle, Offset(center, size.height / 2)) {
                    drawRoundRect(Brush.verticalGradient(listOf(SoftMint, Mint)), Offset(center - 32f, size.height / 2 - height / 2), Size(64f, height), CornerRadius(18f))
                }
            }
        }
    }
}

@Composable
private fun ConnectionPanel(state: CompanionState, dispatch: (CompanionIntent) -> Unit) = GlassPanel {
    Icon(Icons.AutoMirrored.Filled.BluetoothSearching, null, tint = Mint, modifier = Modifier.size(28.dp)); Spacer(Modifier.height(10.dp))
    Text("Conecte seu Robô Olhos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text("Ligue a placa e encontre o dispositivo por Bluetooth.", color = MaterialTheme.colorScheme.secondary); Spacer(Modifier.height(12.dp))
    Button({ dispatch(CompanionIntent.Scan) }, enabled = state.link.phase != LinkPhase.SCANNING, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
        if (state.link.phase == LinkPhase.SCANNING) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.AutoMirrored.Filled.BluetoothSearching, null)
        Spacer(Modifier.width(8.dp)); Text(if (state.link.phase == LinkPhase.SCANNING) "Procurando…" else "Procurar robô")
    }
    state.devices.forEach { device ->
        Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { dispatch(CompanionIntent.Connect(device)) }, color = Color.White.copy(alpha = .06f), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DeveloperBoard, null, tint = Mint); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(device.name, fontWeight = FontWeight.Medium); Text("Sinal ${device.signal} dBm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                Icon(Icons.AutoMirrored.Filled.ChevronRight, null)
            }
        }
    }
}

@Composable
private fun QuickActions(busy: Boolean, dispatch: (CompanionIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ações rápidas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Surpresa" to "/surpresa", "Neutro" to "/neutro", "Demo" to "/demo", "Dormir" to "/dormir").forEach { (label, command) ->
                SuggestionChip({ dispatch(CompanionIntent.Command(command)) }, { Text(label, maxLines = 1) }, modifier = Modifier.weight(1f), enabled = !busy, colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color.White.copy(alpha = .05f)))
            }
        }
    }
}

@Composable
private fun ConversationPanel(state: CompanionState, dispatch: (CompanionIntent) -> Unit) = GlassPanel {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Conversa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(if (state.localAiAvailable) "Contexto local no iPhone" else "Memória local no ESP32", style = MaterialTheme.typography.bodySmall, color = if (state.localAiAvailable) Violet else Mint)
        }
        if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
    }
    Spacer(Modifier.height(14.dp))
    Column(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 330.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { state.messages.takeLast(4).forEach { MessageBubble(it) } }
    Spacer(Modifier.height(14.dp))
    OutlinedTextField(
        state.draft, { dispatch(CompanionIntent.DraftChanged(it)) }, placeholder = { Text("Converse com seu robô…") }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy, maxLines = 4, shape = RoundedCornerShape(20.dp),
        leadingIcon = { IconButton({ dispatch(CompanionIntent.Listen) }, enabled = !state.busy && !state.voice.listening) { Icon(if (state.voice.listening) Icons.Default.GraphicEq else Icons.Default.Mic, "Falar", tint = if (state.voice.listening) Mint else LocalContentColor.current) } },
        trailingIcon = { FilledIconButton({ dispatch(CompanionIntent.Send) }, enabled = state.draft.isNotBlank() && !state.busy) { Icon(Icons.Default.ArrowUpward, "Enviar") } },
    )
    Text(state.voice.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 8.dp, top = 7.dp))
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (message.fromRobot) Alignment.CenterStart else Alignment.CenterEnd) {
        Surface(color = if (message.fromRobot) Color.White.copy(alpha = .07f) else Mint.copy(alpha = .16f), shape = if (message.fromRobot) RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp) else RoundedCornerShape(20.dp, 6.dp, 20.dp, 20.dp), border = BorderStroke(1.dp, if (message.fromRobot) Color.White.copy(alpha = .06f) else Mint.copy(alpha = .18f)), modifier = Modifier.widthIn(max = 290.dp)) {
            Text(message.text, Modifier.padding(horizontal = 14.dp, vertical = 11.dp), maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CorrectionPanel(dispatch: (CompanionIntent) -> Unit) {
    Surface(color = Color(0xFF1A2633).copy(alpha = .8f), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Tune, null, tint = Mint); Spacer(Modifier.width(9.dp)); Text("Ele entendeu errado?", fontWeight = FontWeight.Medium) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("feliz", "triste", "raiva", "neutro").forEach { emotion -> TextButton({ dispatch(CompanionIntent.Correct(emotion)) }) { Text(emotion.replaceFirstChar { it.uppercase() }) } } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrainingPanel(state: CompanionState, dispatch: (CompanionIntent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Surface(color = Glass, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .08f)), modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(17.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Mint.copy(alpha = .13f), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.School, null, tint = Mint, modifier = Modifier.padding(9.dp)) }
                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Ensinar uma conversa", fontWeight = FontWeight.SemiBold); Text("${state.robot.dialogues}/10 memórias na placa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ensine uma resposta e a expressão que deve acompanhá-la.", color = MaterialTheme.colorScheme.secondary)
                    OutlinedTextField(state.trainingPrompt, { dispatch(CompanionIntent.TrainingPromptChanged(it)) }, label = { Text("Quando eu disser…") }, supportingText = { Text("${state.trainingPrompt.length}/80") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                    OutlinedTextField(state.trainingReply, { dispatch(CompanionIntent.TrainingReplyChanged(it)) }, label = { Text("Responda…") }, supportingText = { Text("${state.trainingReply.length}/120") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("feliz", "triste", "raiva", "neutro", "surpresa", "curioso", "sono").forEach { face -> FilterChip(state.trainingFace == face, { dispatch(CompanionIntent.TrainingFaceChanged(face)) }, { Text(face.replaceFirstChar { it.uppercase() }) }) } }
                    Button({ dispatch(CompanionIntent.TrainConversation) }, enabled = !state.busy && state.trainingPrompt.isNotBlank() && state.trainingReply.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.School, null); Spacer(Modifier.width(8.dp)); Text("Ensinar ao robô") }
                }
            }
        }
    }
}

@Composable
private fun GlassPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = Glass, border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))) { Column(Modifier.fillMaxWidth().padding(17.dp), content = content) }
}
