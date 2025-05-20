package fr.isen.improta.airtech

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import fr.isen.improta.airtech.ui.theme.AirtechTheme
import java.util.UUID

class DeviceActivity : ComponentActivity() {

    private var gatt: BluetoothGatt? = null
    private var co2Char: BluetoothGattCharacteristic? = null
    private var pmChar: BluetoothGattCharacteristic? = null

    private val co2Value = mutableStateOf(0)
    private val pmValue = mutableStateOf(0)
    private val connectionState = mutableStateOf("Appuyez sur le bouton pour vous connecter")
    private val isSubscribed = mutableStateOf(false)
    private var skipNextCO2Notification = false
    private var skipNextPMNotification = false

    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val name = intent.getStringExtra("name") ?: "Appareil inconnu"
        val address = intent.getStringExtra("address") ?: "N/A"
        val rssi = intent.getIntExtra("rssi", 0)

        setContent {
            AirtechTheme {
                DeviceScreen(
                    name = name,
                    address = address,
                    rssi = rssi,
                    connectionStatus = connectionState.value,
                    isConnected = connectionState.value.startsWith("✅"),
                    co2Value = co2Value.value,
                    pmValue = pmValue.value,
                    isSubscribed = isSubscribed.value,
                    onBack = { finish() },
                    onConnectClick = { connectToDevice(address, name) },
                    onToggleSubscription = { enable -> toggleNotifications(enable) }
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(address: String, name: String) {
        connectionState.value = "Connexion BLE en cours..."
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val device = bluetoothAdapter.getRemoteDevice(address)

        gatt = device.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gattParam: BluetoothGatt, status: Int, newState: Int) {
                runOnUiThread {
                    if (newState == BluetoothGatt.STATE_CONNECTED) {
                        connectionState.value = "✅ Connecté à $name"
                        gattParam.discoverServices()
                    } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                        connectionState.value = "❌ Déconnecté"
                    }
                }
            }

            override fun onServicesDiscovered(gattParam: BluetoothGatt, status: Int) {
                val co2ServiceUUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
                val co2CharUUID = UUID.fromString("00005678-0000-1000-8000-00805f9b34fb")
                val pmCharUUID = UUID.fromString("00008765-0000-1000-8000-00805f9b34fb")

                val service = gattParam.services.find { it.uuid == co2ServiceUUID }
                co2Char = service?.getCharacteristic(co2CharUUID)
                pmChar = service?.getCharacteristic(pmCharUUID)

                Log.d("BLE", "CO2 char = $co2Char")
                Log.d("BLE", "PM char = $pmChar")

                // Active automatiquement les notifications dès que les caractéristiques sont récupérées
                runOnUiThread {
                    toggleNotifications(true)
                }
            }

            override fun onCharacteristicChanged(
                gattParam: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                when (characteristic.uuid) {
                    co2Char?.uuid -> {
                        if (skipNextCO2Notification) {
                            skipNextCO2Notification = false
                            Log.d("BLE", "Notification CO2 ignorée")
                            return
                        }
                        val raw = characteristic.value
                        if (raw.size >= 2) {
                            val ppm = (raw[0].toInt() and 0xFF) or ((raw[1].toInt() and 0xFF) shl 8)
                            runOnUiThread {
                                co2Value.value = ppm
                            }
                            Log.d("BLE", "📥 CO2 → $ppm ppm")
                        }
                    }
                    pmChar?.uuid -> {
                        if (skipNextPMNotification) {
                            skipNextPMNotification = false
                            Log.d("BLE", "Notification PM ignorée")
                            return
                        }
                        val raw = characteristic.value
                        if (raw.size >= 2) {
                            val pm = (raw[0].toInt() and 0xFF) or ((raw[1].toInt() and 0xFF) shl 8)
                            runOnUiThread {
                                pmValue.value = pm
                            }
                            Log.d("BLE", "📥 PM → $pm µg/m3")
                        }
                    }
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun toggleNotifications(enable: Boolean) {
        co2Char?.let { char ->
            gatt?.setCharacteristicNotification(char, enable)
            val descriptor = char.getDescriptor(CCCD_UUID) ?: return
            descriptor.value = if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            gatt?.writeDescriptor(descriptor)
        }

        pmChar?.let { char ->
            gatt?.setCharacteristicNotification(char, enable)
            val descriptor = char.getDescriptor(CCCD_UUID) ?: return
            descriptor.value = if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            gatt?.writeDescriptor(descriptor)
        }

        runOnUiThread {
            isSubscribed.value = enable
        }

        if (enable) {
            skipNextCO2Notification = true
            skipNextPMNotification = true
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        gatt?.close()
    }
}
