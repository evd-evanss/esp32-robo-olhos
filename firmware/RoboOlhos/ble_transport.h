#pragma once
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLE2902.h>

String respond(const String& original);
String stateJson();
String jsonString(const String& value);

constexpr char BLE_SERVICE_UUID[] = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
constexpr char BLE_RX_UUID[] = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
constexpr char BLE_TX_UUID[] = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";

BLEServer* bleServer = nullptr;
BLECharacteristic* bleTx = nullptr;
volatile bool bleConnected = false;
portMUX_TYPE bleMux = portMUX_INITIALIZER_UNLOCKED;
char bleInput[270] = {};
size_t bleInputLength = 0;
char blePending[270] = {};
volatile bool blePendingReady = false;

class RobotBleServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer*) override { bleConnected = true; }
    void onDisconnect(BLEServer* server) override {
        portENTER_CRITICAL(&bleMux);
        bleConnected = false; bleInputLength = 0; blePendingReady = false;
        portEXIT_CRITICAL(&bleMux);
        delay(30);
        server->startAdvertising();
    }
};

class RobotBleInputCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* characteristic) override {
        String value = characteristic->getValue();
        portENTER_CRITICAL(&bleMux);
        for (size_t i = 0; i < value.length(); ++i) {
            char c = value[i];
            if (c == '\r') continue;
            if (c == '\n') {
                if (!blePendingReady && bleInputLength) {
                    memcpy(blePending, bleInput, bleInputLength);
                    blePending[bleInputLength] = 0;
                    blePendingReady = true;
                }
                bleInputLength = 0;
            } else if (bleInputLength + 1 < sizeof(bleInput)) {
                bleInput[bleInputLength++] = c;
            } else {
                bleInputLength = 0;
            }
        }
        portEXIT_CRITICAL(&bleMux);
    }
};

void setupBluetooth(const String& deviceName) {
    BLEDevice::init(deviceName.c_str());
    bleServer = BLEDevice::createServer();
    bleServer->setCallbacks(new RobotBleServerCallbacks());
    BLEService* service = bleServer->createService(BLE_SERVICE_UUID);
    bleTx = service->createCharacteristic(BLE_TX_UUID, BLECharacteristic::PROPERTY_INDICATE);
    bleTx->addDescriptor(new BLE2902());
    BLECharacteristic* rx = service->createCharacteristic(BLE_RX_UUID, BLECharacteristic::PROPERTY_WRITE);
    rx->setCallbacks(new RobotBleInputCallbacks());
    service->start();
    BLEAdvertising* advertising = BLEDevice::getAdvertising();
    advertising->addServiceUUID(BLE_SERVICE_UUID);
    advertising->setScanResponse(true);
    advertising->start();
}

void sendBluetoothLine(const String& line) {
    if (!bleConnected || !bleTx) return;
    const uint8_t* bytes = reinterpret_cast<const uint8_t*>(line.c_str());
    for (size_t offset = 0; offset < line.length() && bleConnected; offset += 20) {
        size_t count = min((size_t)20, line.length() - offset);
        bleTx->setValue(bytes + offset, count);
        bleTx->indicate();
        delay(8);
    }
}

void handleBluetooth() {
    if (!blePendingReady) return;
    char local[270];
    portENTER_CRITICAL(&bleMux);
    snprintf(local, sizeof(local), "%s", blePending);
    blePendingReady = false;
    portEXIT_CRITICAL(&bleMux);

    char* separator = strchr(local, '\t');
    String id = separator ? String(local).substring(0, separator - local) : "0";
    String text = separator ? String(separator + 1) : "";
    bool validId = id.length() > 0 && id.length() <= 10;
    for (size_t i = 0; i < id.length(); ++i) validId &= id[i] >= '0' && id[i] <= '9';
    String payload;
    if (!validId || text.isEmpty() || text.length() > 240 || text.indexOf('\t') >= 0) {
        payload = "{\"id\":" + (validId ? id : "0") + ",\"error\":\"Requisicao Bluetooth invalida.\",\"state\":" + stateJson() + "}\n";
    } else {
        String reply = text == "/status" ? "" : respond(text);
        payload = "{\"id\":" + id + ",\"reply\":" + jsonString(reply) + ",\"state\":" + stateJson() + "}\n";
    }
    sendBluetoothLine(payload);
}
