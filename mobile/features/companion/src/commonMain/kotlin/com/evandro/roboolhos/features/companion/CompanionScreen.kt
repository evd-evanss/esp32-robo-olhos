package com.evandro.roboolhos.features.companion

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evandro.roboolhos.core.designsystem.Mint
import com.evandro.roboolhos.core.robot.LinkPhase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionScreen(viewModel: CompanionViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Robô Olhos", fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = { viewModel.dispatch(CompanionIntent.ToggleSound) }) {
                    Icon(if (state.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff, "Ativar ou desativar voz")
                }
            },
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { EyesCard(state.robot.face, state.link.phase, state.link.detail) }
            if (state.link.phase != LinkPhase.READY) item { ConnectionCard(state, viewModel::dispatch) }
            else {
                item { QuickActions(state.busy, viewModel::dispatch) }
                item { ChatCard(state, viewModel::dispatch) }
                if (state.lastUserText.isNotBlank()) item { CorrectionCard(viewModel::dispatch) }
                item { TrainingCard(state, viewModel::dispatch) }
            }
        }
    }
    state.error?.let { error ->
        AlertDialog(onDismissRequest = { viewModel.dispatch(CompanionIntent.DismissError) }, confirmButton = {
            TextButton(onClick = { viewModel.dispatch(CompanionIntent.DismissError) }) { Text("Entendi") }
        }, title = { Text("Não foi possível continuar") }, text = { Text(error) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrainingCard(state: CompanionState, dispatch: (CompanionIntent) -> Unit) {
    val faces = listOf("feliz", "triste", "raiva", "neutro", "surpresa", "curioso", "sono")
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ensinar uma conversa", style = MaterialTheme.typography.titleMedium)
            Text("Defina o que você diz, como ele responde e a expressão que fará. Frases com palavras parecidas também serão reconhecidas.", color = MaterialTheme.colorScheme.secondary)
            OutlinedTextField(
                value = state.trainingPrompt,
                onValueChange = { dispatch(CompanionIntent.TrainingPromptChanged(it)) },
                label = { Text("Quando eu disser…") },
                supportingText = { Text("${state.trainingPrompt.length}/80") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.trainingReply,
                onValueChange = { dispatch(CompanionIntent.TrainingReplyChanged(it)) },
                label = { Text("Responda…") },
                supportingText = { Text("${state.trainingReply.length}/120") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Expressão", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                faces.forEach { face ->
                    FilterChip(
                        selected = state.trainingFace == face,
                        onClick = { dispatch(CompanionIntent.TrainingFaceChanged(face)) },
                        label = { Text(face.replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            Button(
                onClick = { dispatch(CompanionIntent.TrainConversation) },
                enabled = !state.busy && state.trainingPrompt.isNotBlank() && state.trainingReply.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Ensinar ao robô") }
            Text("${state.robot.dialogues}/10 conversas salvas na placa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun EyesCard(face: String, phase: LinkPhase, detail: String) {
    val transition = rememberInfiniteTransition(label = "piscar")
    val blink by transition.animateFloat(1f, .08f, infiniteRepeatable(tween(130), RepeatMode.Reverse), label = "altura")
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.fillMaxWidth().height(112.dp).semantics { contentDescription = "Expressão $face" }) {
                val h = when (face) { "sono" -> 7f; "surpresa" -> 68f; "feliz" -> 34f; else -> 54f } * if (face == "sono") 1f else blink.coerceAtLeast(.75f)
                listOf(size.width * .34f, size.width * .66f).forEachIndexed { index, center ->
                    rotate(when (face) { "raiva" -> if (index == 0) 12f else -12f; "triste" -> if (index == 0) -10f else 10f; else -> 0f }, Offset(center, size.height / 2)) {
                        drawRoundRect(Mint, Offset(center - 31f, size.height / 2 - h / 2), Size(62f, if (face == "curioso" && index == 1) h * .65f else h), CornerRadius(16f))
                    }
                }
            }
            Text(if (phase == LinkPhase.READY) "Conectado • expressão $face" else detail, color = if (phase == LinkPhase.READY) Mint else MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun ConnectionCard(state: CompanionState, dispatch: (CompanionIntent) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Conectar por Bluetooth", style = MaterialTheme.typography.titleMedium)
        Text("Ligue o robô com o firmware atualizado e procure por RoboOlhos.", color = MaterialTheme.colorScheme.secondary)
        Button(onClick = { dispatch(CompanionIntent.Scan) }, enabled = state.link.phase != LinkPhase.SCANNING, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.BluetoothSearching, null); Spacer(Modifier.width(8.dp)); Text(if (state.link.phase == LinkPhase.SCANNING) "Procurando…" else "Procurar robô")
        }
        state.devices.forEach { device ->
            ListItem(headlineContent = { Text(device.name) }, supportingContent = { Text("Sinal ${device.signal} dBm") }, modifier = Modifier.clickable { dispatch(CompanionIntent.Connect(device)) })
        }
        if (state.link.phase == LinkPhase.SCANNING && state.devices.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
    } }
}

@Composable
private fun QuickActions(busy: Boolean, dispatch: (CompanionIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Expressões rápidas", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Surpresa" to "/surpresa", "Neutro" to "/neutro", "Demo" to "/demo", "Dormir" to "/dormir").forEach { (label, cmd) ->
                OutlinedButton({ dispatch(CompanionIntent.Command(cmd)) }, Modifier.weight(1f), enabled = !busy, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(label) }
            }
        }
    }
}

@Composable
private fun ChatCard(state: CompanionState, dispatch: (CompanionIntent) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Conversa", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.messages.takeLast(6).forEach { message ->
                Box(Modifier.align(if (message.fromRobot) Alignment.Start else Alignment.End).background(if (message.fromRobot) MaterialTheme.colorScheme.surfaceVariant else Mint.copy(alpha = .2f), RoundedCornerShape(14.dp)).padding(12.dp)) { Text(message.text) }
            }
        }
        OutlinedTextField(value = state.draft, onValueChange = { dispatch(CompanionIntent.DraftChanged(it)) }, label = { Text("Digite ou use o microfone") }, modifier = Modifier.fillMaxWidth(), maxLines = 3, enabled = !state.busy)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { dispatch(CompanionIntent.Listen) }, modifier = Modifier.weight(1f), enabled = !state.busy && !state.voice.listening) {
                Icon(Icons.Default.Mic, null); Spacer(Modifier.width(6.dp)); Text(if (state.voice.listening) "Ouvindo…" else "Falar")
            }
            Button(onClick = { dispatch(CompanionIntent.Send) }, modifier = Modifier.weight(1f), enabled = state.draft.isNotBlank() && !state.busy) {
                Icon(Icons.Default.Send, null); Spacer(Modifier.width(6.dp)); Text("Enviar")
            }
        }
        Text(state.voice.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    } }
}

@Composable
private fun CorrectionCard(dispatch: (CompanionIntent) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
        Text("O robô entendeu errado?", style = MaterialTheme.typography.titleMedium)
        Text("Ensine a emoção da sua última frase.", color = MaterialTheme.colorScheme.secondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("feliz", "triste", "raiva", "neutro").forEach { emotion -> TextButton({ dispatch(CompanionIntent.Correct(emotion)) }) { Text(emotion.replaceFirstChar { it.uppercase() }) } }
        }
    } }
}
