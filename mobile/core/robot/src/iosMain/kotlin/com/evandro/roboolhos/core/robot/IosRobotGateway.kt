@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.evandro.roboolhos.core.robot

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import platform.CoreBluetooth.*
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.dataWithBytes
import platform.darwin.NSObject

class IosRobotGateway : RobotGateway {
    private val serviceUuid = CBUUID.UUIDWithString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    private val rxUuid = CBUUID.UUIDWithString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    private val txUuid = CBUUID.UUIDWithString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableConnection = MutableStateFlow(LinkState())
    override val connection: StateFlow<LinkState> = mutableConnection
    private val mutableDevices = MutableStateFlow<List<RobotDevice>>(emptyList())
    override val devices: StateFlow<List<RobotDevice>> = mutableDevices
    private val peripherals = linkedMapOf<String, CBPeripheral>()
    private val signals = mutableMapOf<String, Int>()
    private val mutex = Mutex()
    private val incoming = StringBuilder()
    private val bluetoothDelegate = RobotBluetoothDelegate(this)
    private var nextId = 1L
    private lateinit var central: CBCentralManager
    private var peripheral: CBPeripheral? = null
    private var rx: CBCharacteristic? = null
    private var tx: CBCharacteristic? = null
    private var connectWaiter: CompletableDeferred<Unit>? = null
    private var bluetoothReadyWaiter: CompletableDeferred<Unit>? = null
    private var writeWaiter: CompletableDeferred<Unit>? = null
    private var responseWaiter: CompletableDeferred<RobotReply>? = null

    init { central = CBCentralManager(bluetoothDelegate, null) }

    override suspend fun scan() {
        awaitBluetooth()
        stopScan(); peripherals.clear(); signals.clear(); mutableDevices.value = emptyList()
        mutableConnection.value = LinkState(LinkPhase.SCANNING, "Procurando RoboOlhos…")
        // Scan broadly and validate the advertised name ourselves. Some ESP32
        // stacks place the 128-bit service in the scan response, which iOS does
        // not always match reliably when the scan starts immediately after boot.
        central.scanForPeripheralsWithServices(null, null)
        delay(10_000)
        if (mutableConnection.value.phase == LinkPhase.SCANNING) {
            stopScan()
            mutableConnection.value = LinkState(detail = if (peripherals.isEmpty()) "Nenhum robô encontrado" else "Escolha o robô")
        }
    }
    override fun stopScan() { if (::central.isInitialized) central.stopScan() }
    override suspend fun connect(device: RobotDevice) {
        stopScan(); disconnect()
        val target = peripherals[device.id] ?: throw IllegalStateException("Procure o robô novamente.")
        peripheral = target; target.delegate = bluetoothDelegate
        mutableConnection.value = LinkState(LinkPhase.CONNECTING, "Conectando…", device.name)
        connectWaiter = CompletableDeferred()
        central.connectPeripheral(target, null)
        withTimeout(15_000) { connectWaiter!!.await() }
    }
    override fun disconnect() {
        peripheral?.let { central.cancelPeripheralConnection(it) }
        peripheral = null; rx = null; tx = null
        failPending("Conexão encerrada.")
        mutableConnection.value = LinkState()
    }
    override suspend fun send(text: String): RobotReply = mutex.withLock {
        require(text.encodeToByteArray().size <= 240) { "Mensagem muito longa." }
        val target = peripheral ?: throw IllegalStateException("Robô desconectado.")
        val input = rx ?: throw IllegalStateException("Canal Bluetooth indisponível.")
        val id = nextId++
        responseWaiter = CompletableDeferred(); incoming.clear()
        "$id\t$text\n".encodeToByteArray().asList().chunked(20).forEach { part ->
            writeWaiter = CompletableDeferred()
            target.writeValue(part.toByteArray().toNSData(), input, CBCharacteristicWriteWithResponse)
            withTimeout(3_000) { writeWaiter!!.await() }
        }
        withTimeout(12_000) { responseWaiter!!.await() }.also {
            if (it.id != id) throw IllegalStateException("Resposta Bluetooth fora de sequência.")
        }
    }

    internal fun onCentralStateUpdated(central: CBCentralManager) {
        if (central.state == CBManagerStatePoweredOn) {
            bluetoothReadyWaiter?.complete(Unit)
            return
        }
        if (central.state != CBManagerStatePoweredOn && mutableConnection.value.phase != LinkPhase.DISCONNECTED) {
            mutableConnection.value = LinkState(detail = "Bluetooth indisponível")
        }
    }
    internal fun onPeripheralDiscovered(didDiscoverPeripheral: CBPeripheral, RSSI: NSNumber) {
        val name = didDiscoverPeripheral.name ?: return
        if (!name.startsWith("RoboOlhos")) return
        val id = didDiscoverPeripheral.identifier.UUIDString
        peripherals[id] = didDiscoverPeripheral; signals[id] = RSSI.intValue
        mutableDevices.value = peripherals.map { (key, value) -> RobotDevice(key, value.name ?: "RoboOlhos", signals[key] ?: 0) }
    }
    internal fun onPeripheralConnected(didConnectPeripheral: CBPeripheral) {
        didConnectPeripheral.delegate = bluetoothDelegate
        didConnectPeripheral.discoverServices(listOf(serviceUuid))
        mutableConnection.value = LinkState(LinkPhase.CONNECTING, "Configurando comunicação…", didConnectPeripheral.name ?: "RoboOlhos")
    }
    internal fun onPeripheralConnectionFailed(error: NSError?) = failConnect(error?.localizedDescription ?: "Não foi possível conectar.")
    internal fun onPeripheralDisconnected(error: NSError?) {
        failPending("Robô desconectado."); rx = null; tx = null
        mutableConnection.value = LinkState(detail = "Robô desconectado")
    }
    internal fun onServicesDiscovered(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
        if (didDiscoverServices != null) return failConnect(didDiscoverServices.localizedDescription)
        val service = peripheral.services?.filterIsInstance<CBService>()?.firstOrNull { it.UUID == serviceUuid }
            ?: return failConnect("Firmware Bluetooth não encontrado. Atualize o ESP32.")
        peripheral.discoverCharacteristics(listOf(rxUuid, txUuid), service)
    }
    internal fun onCharacteristicsDiscovered(peripheral: CBPeripheral, didDiscoverCharacteristicsForService: CBService, error: NSError?) {
        if (error != null) return failConnect(error.localizedDescription)
        didDiscoverCharacteristicsForService.characteristics?.filterIsInstance<CBCharacteristic>()?.forEach {
            if (it.UUID == rxUuid) rx = it
            if (it.UUID == txUuid) tx = it
        }
        val output = tx ?: return failConnect("Canal de resposta não encontrado.")
        if (rx == null) return failConnect("Canal de envio não encontrado.")
        peripheral.setNotifyValue(true, output)
    }
    internal fun onNotificationStateUpdated(peripheral: CBPeripheral, didUpdateNotificationStateForCharacteristic: CBCharacteristic, error: NSError?) {
        if (error != null || !didUpdateNotificationStateForCharacteristic.isNotifying) return failConnect(error?.localizedDescription ?: "Não foi possível ativar as respostas.")
        mutableConnection.value = LinkState(LinkPhase.READY, "Pronto para conversar", peripheral.name ?: "RoboOlhos")
        connectWaiter?.complete(Unit)
    }
    internal fun onValueWritten(error: NSError?) {
        if (error == null) writeWaiter?.complete(Unit) else writeWaiter?.completeExceptionally(IllegalStateException(error.localizedDescription))
    }
    internal fun onValueUpdated(didUpdateValueForCharacteristic: CBCharacteristic, error: NSError?) {
        if (error != null) return responseWaiter?.completeExceptionally(IllegalStateException(error.localizedDescription)).let { Unit }
        accept(didUpdateValueForCharacteristic.value?.toByteArray() ?: return)
    }

    private fun accept(bytes: ByteArray) {
        incoming.append(bytes.decodeToString())
        while ('\n' in incoming) {
            val end = incoming.indexOf("\n")
            val line = incoming.substring(0, end)
            val remainder = incoming.substring(end + 1)
            incoming.clear()
            incoming.append(remainder)
            runCatching { json.decodeFromString<RobotReply>(line) }
                .onSuccess { responseWaiter?.complete(it) }
                .onFailure { responseWaiter?.completeExceptionally(IllegalStateException("Resposta inválida do robô.")) }
        }
        if (incoming.length > 2048) { incoming.clear(); responseWaiter?.completeExceptionally(IllegalStateException("Resposta grande demais.")) }
    }
    private fun failConnect(message: String) { connectWaiter?.completeExceptionally(IllegalStateException(message)); mutableConnection.value = LinkState(detail = message) }
    private suspend fun awaitBluetooth() {
        if (central.state == CBManagerStatePoweredOn) return
        bluetoothReadyWaiter = CompletableDeferred()
        try {
            withTimeout(5_000) { bluetoothReadyWaiter!!.await() }
        } catch (_: TimeoutCancellationException) {
            throw IllegalStateException("Ative o Bluetooth do iPhone e tente novamente.")
        } finally {
            bluetoothReadyWaiter = null
        }
    }
    private fun failPending(message: String) { val error = IllegalStateException(message); connectWaiter?.completeExceptionally(error); writeWaiter?.completeExceptionally(error); responseWaiter?.completeExceptionally(error) }
    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.dataWithBytes(bytes = pinned.addressOf(0), length = size.convert())
    }
    private fun NSData.toByteArray(): ByteArray = bytes?.reinterpret<ByteVar>()?.readBytes(length.toInt()) ?: byteArrayOf()
}

private class RobotBluetoothDelegate(
    private val gateway: IosRobotGateway,
) : NSObject(), CBCentralManagerDelegateProtocol, CBPeripheralDelegateProtocol {
    override fun centralManagerDidUpdateState(central: CBCentralManager) =
        gateway.onCentralStateUpdated(central)

    override fun centralManager(
        central: CBCentralManager,
        didDiscoverPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        RSSI: NSNumber,
    ) = gateway.onPeripheralDiscovered(didDiscoverPeripheral, RSSI)

    override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) =
        gateway.onPeripheralConnected(didConnectPeripheral)

    @ObjCSignatureOverride
    override fun centralManager(
        central: CBCentralManager,
        didFailToConnectPeripheral: CBPeripheral,
        error: NSError?,
    ) = gateway.onPeripheralConnectionFailed(error)

    @ObjCSignatureOverride
    override fun centralManager(
        central: CBCentralManager,
        didDisconnectPeripheral: CBPeripheral,
        error: NSError?,
    ) = gateway.onPeripheralDisconnected(error)

    override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) =
        gateway.onServicesDiscovered(peripheral, didDiscoverServices)

    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverCharacteristicsForService: CBService,
        error: NSError?,
    ) = gateway.onCharacteristicsDiscovered(peripheral, didDiscoverCharacteristicsForService, error)

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateNotificationStateForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) = gateway.onNotificationStateUpdated(peripheral, didUpdateNotificationStateForCharacteristic, error)

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didWriteValueForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) = gateway.onValueWritten(error)

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateValueForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) = gateway.onValueUpdated(didUpdateValueForCharacteristic, error)
}
