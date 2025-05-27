package fr.isen.improta.airtech

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.mutableStateOf
import fr.isen.improta.airtech.ui.theme.AirtechTheme
import java.util.UUID

class DeviceActivity : ComponentActivity() {

    private var gatt: BluetoothGatt? = null
    private var switchChar: BluetoothGattCharacteristic? = null

    private val co2Value = mutableStateOf(0)
    private val pmValue = mutableStateOf(0)
    private val connectionState = mutableStateOf("Appuyez sur le bouton pour vous connecter")
    private val isSubscribed = mutableStateOf(false)

    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val CHANNEL_ID = "airtech_notifications"
    private val NOTIF_ID_CO2 = 1
    private val NOTIF_ID_PM = 2

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()

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

    // Créer le canal de notification pour Android 8.0+
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "AirTech Alerts"
            val descriptionText = "Notifications pour niveaux CO2 et PM élevés"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
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

                val serviceUUID = UUID.fromString("0000feed-cc7a-482a-984a-7f2ed5b3e58f")
                val switchCharUUID = UUID.fromString("00001234-8e22-4541-9d4c-21edae82ed19")

                val service = gattParam.services.find { it.uuid == serviceUUID }
                switchChar = service?.getCharacteristic(switchCharUUID)

                Log.d("BLE", "CO2 char = $serviceUUID")
                Log.d("BLE", "PM char = $switchCharUUID")

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

                if (raw.size >= 4) {
                    val pm = (raw[1].toInt() and 0xFF shl 8) or (raw[0].toInt() and 0xFF)
                    val co2 = (raw[3].toInt() and 0xFF shl 8) or (raw[2].toInt() and 0xFF)

                    runOnUiThread {
                        co2Value.value = co2
                        pmValue.value = pm
                    }

                    Log.d("BLE", "📊 CO2 = $co2 ppm | PM = $pm pcs/0.01cf")

                    // Notification si seuil dépassé
                    if (co2 > 100) {
                        sendNotification(
                            NOTIF_ID_CO2,
                            "Alerte Particules",
                            "La concentration en particules a dépassé 100 ppm: $co2 ppm"
                        )
                    }
                    if (pm > 1200) {
                        sendNotification(
                            NOTIF_ID_PM,
                            "Alerte CO2",
                            "La concentration en CO2 a dépassé 1200 pcs/0.01cf: $pm"
                        )
                    }
                } else {
                    Log.w("BLE", "⚠️ Données insuffisantes pour décoder (seulement ${raw.size} octets)")
                }
            }
        })
    }

    // Fonction pour envoyer une notification simple
    private fun sendNotification(id: Int, title: String, content: String) {
        val intent = Intent(this, DeviceActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(id, builder.build())
        }
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
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        gatt?.close()
    }
}
