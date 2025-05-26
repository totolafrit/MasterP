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
//    private var co2Char: BluetoothGattCharacteristic? = null
//    private var pmChar: BluetoothGattCharacteristic? = null
    private var switchChar: BluetoothGattCharacteristic? = null

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

                gattParam.services.forEach { service ->
                    Log.d("BLE", "Service: ${service.uuid}")
                    service.characteristics.forEach { char ->
                        Log.d("BLE", " └ Char: ${char.uuid}")
                    }
                }

//                val co2ServiceUUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
//                val co2CharUUID = UUID.fromString("00005678-0000-1000-8000-00805f9b34fb")
//                val pmCharUUID = UUID.fromString("00008765-0000-1000-8000-00805f9b34fb")

                val serviceUUID = UUID.fromString("0000feed-cc7a-482a-984a-7f2ed5b3e58f")
                val switchCharUUID = UUID.fromString("00001234-8e22-4541-9d4c-21edae82ed19")

                val service = gattParam.services.find { it.uuid == serviceUUID }
                switchChar = service?.getCharacteristic(switchCharUUID)

                //val service = gattParam.services.find { it.uuid == co2ServiceUUID }
                //co2Char = service?.getCharacteristic(co2CharUUID)
                //pmChar = service?.getCharacteristic(pmCharUUID)

                Log.d("BLE", "CO2 char = $serviceUUID")
                Log.d("BLE", "PM char = $switchCharUUID")


                // Active automatiquement les notifications dès que les caractéristiques sont récupérées
                runOnUiThread {
                    toggleNotifications(true)
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                val raw = characteristic.value
                val hex = raw.joinToString(" ") { String.format("%02X", it) }
                Log.d("BLE", "📥 Notification reçue (${raw.size} octets) : $hex")

                if (raw.size >= 2) {
                    val co2 = (raw[1].toInt() and 0xFF shl 8) or (raw[0].toInt() and 0xFF)
                    Log.d("BLE", "CO2 partiel = $co2 ppm")
                }



                if (raw.size >= 4) {
                    val co2 = (raw[1].toInt() and 0xFF shl 8) or (raw[0].toInt() and 0xFF)
                    val pm = (raw[3].toInt() and 0xFF shl 8) or (raw[2].toInt() and 0xFF)

                    runOnUiThread {
                        co2Value.value = co2
                        pmValue.value = pm
                    }

                    Log.d("BLE", "📊 CO2 = $co2 ppm | PM = $pm pcs/0.01cf")
                } else {
                    Log.w("BLE", "⚠️ Données insuffisantes pour décoder (seulement ${raw.size} octets)")
                }
            }

        })
    }

    @SuppressLint("MissingPermission")
    private fun toggleNotifications(enable: Boolean) {
        switchChar?.let { char ->
            gatt?.setCharacteristicNotification(char, enable)
            val descriptor = char.getDescriptor(CCCD_UUID) ?: return
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt?.writeDescriptor(descriptor)

            Log.d("BLE", "🔔 Notification activée sur switchChar: ${char.uuid}")
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
