package com.evandro.roboolhos.core.robot

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

@SuppressLint("MissingPermission")
class AndroidRobotGateway(context: Context) : RobotGateway {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter get() = manager.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val ids = AtomicLong(1)
    private val mutableConnection = MutableStateFlow(LinkState())
    override val connection: StateFlow<LinkState> = mutableConnection
    private val mutableDevices = MutableStateFlow<List<RobotDevice>>(emptyList())
    override val devices: StateFlow<List<RobotDevice>> = mutableDevices
    private val found = linkedMapOf<String, BluetoothDevice>()
    private var gatt: BluetoothGatt? = null
    private var rx: BluetoothGattCharacteristic? = null
    private var tx: BluetoothGattCharacteristic? = null
    private var connectWaiter: CompletableDeferred<Unit>? = null
    private var writeWaiter: CompletableDeferred<Unit>? = null
    private var responseWaiter: CompletableDeferred<RobotReply>? = null
    private val incoming = StringBuilder()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: result.scanRecord?.deviceName ?: return
            if (!name.startsWith("RoboOlhos")) return
            found[result.device.address] = result.device
            mutableDevices.value = found.map { (id, device) -> RobotDevice(id, device.name ?: "RoboOlhos", if (id == result.device.address) result.rssi else 0) }
        }
        override fun onScanFailed(errorCode: Int) {
            mutableConnection.value = LinkState(detail = "Falha ao procurar Bluetooth ($errorCode)")
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                mutableConnection.value = LinkState(LinkPhase.CONNECTING, "Configurando comunicação…", g.device.name ?: "RoboOlhos")
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                failPending("Robô desconectado.")
                rx = null; tx = null
                mutableConnection.value = LinkState(detail = "Robô desconectado")
            }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return failConnect("Serviços Bluetooth indisponíveis.")
            val service = g.getService(SERVICE) ?: return failConnect("Firmware Bluetooth não encontrado. Atualize o ESP32.")
            rx = service.getCharacteristic(RX)
            tx = service.getCharacteristic(TX)
            val output = tx ?: return failConnect("Canal de resposta não encontrado.")
            g.setCharacteristicNotification(output, true)
            val descriptor = output.getDescriptor(CCCD) ?: return failConnect("Notificações não disponíveis.")
            if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
            else { @Suppress("DEPRECATION") descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE; @Suppress("DEPRECATION") g.writeDescriptor(descriptor) }
        }
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mutableConnection.value = LinkState(LinkPhase.READY, "Pronto para conversar", g.device.name ?: "RoboOlhos")
                connectWaiter?.complete(Unit)
            } else failConnect("Não foi possível ativar as respostas.")
        }
        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) writeWaiter?.complete(Unit) else writeWaiter?.completeExceptionally(IllegalStateException("Falha ao enviar ($status)."))
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) = accept(value)
        @Deprecated("Android 13 callback compatibility")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) = accept(characteristic.value ?: byteArrayOf())
    }

    override suspend fun scan() {
        if (adapter == null || !adapter.isEnabled) throw IllegalStateException("Ative o Bluetooth do celular.")
        stopScan(); found.clear(); mutableDevices.value = emptyList()
        mutableConnection.value = LinkState(LinkPhase.SCANNING, "Procurando RoboOlhos…")
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build()
        adapter.bluetoothLeScanner.startScan(listOf(filter), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        scope.launch { delay(10_000); if (mutableConnection.value.phase == LinkPhase.SCANNING) { stopScan(); mutableConnection.value = LinkState(detail = if (found.isEmpty()) "Nenhum robô encontrado" else "Escolha o robô") } }
    }
    override fun stopScan() { runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) } }
    override suspend fun connect(device: RobotDevice) {
        stopScan(); disconnect()
        val target = found[device.id] ?: adapter.getRemoteDevice(device.id)
        mutableConnection.value = LinkState(LinkPhase.CONNECTING, "Conectando…", device.name)
        connectWaiter = CompletableDeferred()
        gatt = target.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        withTimeout(15_000) { connectWaiter!!.await() }
    }
    override fun disconnect() { stopScan(); gatt?.disconnect(); gatt?.close(); gatt = null; failPending("Conexão encerrada."); mutableConnection.value = LinkState() }

    override suspend fun send(text: String): RobotReply = mutex.withLock {
        require(text.toByteArray().size <= 240) { "Mensagem muito longa." }
        val g = gatt ?: throw IllegalStateException("Robô desconectado.")
        val input = rx ?: throw IllegalStateException("Canal Bluetooth indisponível.")
        val id = ids.getAndIncrement()
        responseWaiter = CompletableDeferred()
        incoming.clear()
        val bytes = "$id\t$text\n".encodeToByteArray()
        bytes.asList().chunked(20).forEach { part ->
            writeWaiter = CompletableDeferred()
            val value = part.toByteArray()
            val started = if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(input, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            else { @Suppress("DEPRECATION") input.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT; @Suppress("DEPRECATION") input.value = value; @Suppress("DEPRECATION") g.writeCharacteristic(input) }
            if (!started) throw IllegalStateException("Bluetooth ocupado.")
            withTimeout(3_000) { writeWaiter!!.await() }
        }
        withTimeout(12_000) { responseWaiter!!.await() }.also { if (it.id != id) throw IllegalStateException("Resposta Bluetooth fora de sequência.") }
    }

    private fun accept(bytes: ByteArray) {
        incoming.append(bytes.decodeToString())
        while ('\n' in incoming) {
            val end = incoming.indexOf("\n")
            val line = incoming.substring(0, end)
            incoming.delete(0, end + 1)
            runCatching { json.decodeFromString<RobotReply>(line) }.onSuccess { responseWaiter?.complete(it) }
                .onFailure { responseWaiter?.completeExceptionally(IllegalStateException("Resposta inválida do robô.")) }
        }
        if (incoming.length > 2048) { incoming.clear(); responseWaiter?.completeExceptionally(IllegalStateException("Resposta grande demais.")) }
    }
    private fun failConnect(message: String) { connectWaiter?.completeExceptionally(IllegalStateException(message)); mutableConnection.value = LinkState(detail = message) }
    private fun failPending(message: String) { responseWaiter?.completeExceptionally(IllegalStateException(message)); writeWaiter?.completeExceptionally(IllegalStateException(message)); connectWaiter?.completeExceptionally(IllegalStateException(message)) }
    companion object {
        val SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val RX: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val TX: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
