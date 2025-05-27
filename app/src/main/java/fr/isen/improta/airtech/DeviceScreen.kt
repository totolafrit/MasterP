package fr.isen.improta.airtech

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    name: String,
    address: String,
    rssi: Int,
    connectionStatus: String,
    isConnected: Boolean,
    co2Value: Int,
    pmValue: Int,
    isSubscribed: Boolean,
    onBack: () -> Unit,
    onConnectClick: () -> Unit,
    onToggleSubscription: (Boolean) -> Unit
) {
    // Etats pour garder les anciennes valeurs de CO2 et PM
    val co2History = rememberHistoryState()
    val pmHistory = rememberHistoryState()

    // Mettre à jour l'historique des valeurs
    if (isConnected) {
        co2History.add(co2Value)
        pmHistory.add(pmValue)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Air Quality Monitor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Bloc de connexion (visible uniquement si non connecté)
            if (!isConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Nom : $name", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Adresse : $address", fontSize = 14.sp, color = Color.Gray)
                        Text("RSSI : $rssi dBm", fontSize = 14.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Statut : $connectionStatus", fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            enabled = !isConnected,
                            onClick = onConnectClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Se connecter")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSubscribed,
                                onCheckedChange = onToggleSubscription
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Recevoir notifications")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Bloc affichage des données CO2 et PM (visible uniquement si connecté)
            if (isConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD1C4E9)),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).align(Alignment.CenterHorizontally),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Concentration Particules (PM)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFFE64A19)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "$co2Value ppm",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFCCBC)),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).align(Alignment.CenterHorizontally),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Concentration CO₂",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFF512DA8)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "$pmValue µg/m³",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Graphique de CO2 et PM
                Spacer(modifier = Modifier.height(24.dp))

                // Graphique pour CO2 avec historique
                GraphView(
                    title = "C02",
                    values = pmHistory.values,
                    scaleFactor = 2000f
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Graphique pour PM avec historique
                GraphView(
                    title = "PPM",
                    values = co2History.values,
                    scaleFactor = 200f
                )
            }
        }
    }
}

// Fonction pour mémoriser l'historique des valeurs
@Composable
fun rememberHistoryState(): HistoryState {
    val state = remember { HistoryState() }
    return state
}

// Classe pour gérer l'historique des valeurs
class HistoryState {
    private val _values = mutableListOf<Int>()
    val values: List<Int> get() = _values

    fun add(value: Int) {
        _values.add(value)
        if (_values.size > 10) { // Garder seulement les 10 dernières valeurs
            _values.removeAt(0)
        }
    }
}

@Composable
fun GraphView(title: String, values: List<Int>, scaleFactor: Float) {
    if (values.isEmpty()) return

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val paddingLeft = 60f
        val paddingBottom = 50f
        val paddingTop = 30f
        val paddingRight = 20f

        val usableWidth = canvasWidth - paddingLeft - paddingRight
        val usableHeight = canvasHeight - paddingTop - paddingBottom

        // Paint pour texte
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 40f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }

        // Fond clair
        drawRect(
            color = Color(0xFFF0F0F0),
            topLeft = Offset(paddingLeft, paddingTop),
            size = androidx.compose.ui.geometry.Size(usableWidth, usableHeight)
        )

        // Grille horizontale (5 lignes)
        val gridLines = 5
        for (i in 0..gridLines) {
            val y = paddingTop + i * usableHeight / gridLines
            drawLine(
                color = Color(0xFFCCCCCC),
                start = Offset(paddingLeft, y),
                end = Offset(canvasWidth - paddingRight, y),
                strokeWidth = 1f
            )
            // Valeurs graduées sur l'axe Y
            val labelValue = ((gridLines - i) * scaleFactor / gridLines).toInt()
            drawContext.canvas.nativeCanvas.drawText(
                "$labelValue",
                paddingLeft - 10f,
                y + 12f,
                paint
            )
        }

        // Axe X et Y
        drawLine(
            color = Color.Black,
            start = Offset(paddingLeft, paddingTop),
            end = Offset(paddingLeft, canvasHeight - paddingBottom),
            strokeWidth = 3f
        )
        drawLine(
            color = Color.Black,
            start = Offset(paddingLeft, canvasHeight - paddingBottom),
            end = Offset(canvasWidth - paddingRight, canvasHeight - paddingBottom),
            strokeWidth = 3f
        )

        // Points espacés sur X
        val pointCount = values.size
        val gapX = if (pointCount > 1) usableWidth / (pointCount - 1) else usableWidth

        // Calcul échelle Y
        val scaleY = usableHeight / scaleFactor

        // Dessiner la ligne avec courbure lissée
        val lineColor = if (title == "CO2") Color(0xFFE64A19) else Color(0xFF512DA8)
        val strokeWidth = 4f

        for (i in 1 until pointCount) {
            val x1 = paddingLeft + gapX * (i - 1)
            val y1 = canvasHeight - paddingBottom - values[i - 1] * scaleY
            val x2 = paddingLeft + gapX * i
            val y2 = canvasHeight - paddingBottom - values[i] * scaleY

            drawLine(
                color = lineColor,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        // Dessiner les points
        for (i in 0 until pointCount) {
            val x = paddingLeft + gapX * i
            val y = canvasHeight - paddingBottom - values[i] * scaleY
            drawCircle(
                color = lineColor,
                radius = 8f,
                center = Offset(x, y)
            )
        }

        // Titre en haut à gauche
        val titlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 50f
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.LEFT
        }
        drawContext.canvas.nativeCanvas.drawText(
            title,
            paddingLeft,
            paddingTop - 10f,
            titlePaint
        )

        // Dernière valeur en haut à droite
        val valuePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 40f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        drawContext.canvas.nativeCanvas.drawText(
            "${values.last()}",
            canvasWidth - paddingRight,
            paddingTop - 10f,
            valuePaint
        )
    }
}
