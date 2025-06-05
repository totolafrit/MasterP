package fr.isen.improta.airtech.Screen

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle.Companion.Fill
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
fun GraphView(
    title: String,
    values: List<Int>,
    scaleFactor: Float
) {
    if (values.isEmpty()) return

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(horizontal = 12.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Marges pour laisser de la place aux labels
        val marginLeft = 50f
        val marginBottom = 50f
        val marginTop = 40f
        val marginRight = 20f

        val plotWidth = canvasWidth - marginLeft - marginRight
        val plotHeight = canvasHeight - marginTop - marginBottom

        // 1) Fond blanc + grille horizontale
        drawRect(
            color = Color.White,
            topLeft = Offset(0f, 0f),
            size = size
        )

        val gridPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY
            strokeWidth = 1f
            isAntiAlias = true
        }
        val horizontalLines = 5
        for (i in 0..horizontalLines) {
            val y = marginTop + i * (plotHeight / horizontalLines)
            drawLine(
                color = Color.LightGray,
                start = Offset(marginLeft, y),
                end = Offset(canvasWidth - marginRight, y),
                strokeWidth = 1f
            )

            // Label Y
            val labelValue = ((horizontalLines - i) * scaleFactor / horizontalLines).toInt()
            drawContext.canvas.nativeCanvas.drawText(
                "$labelValue",
                marginLeft - 8f,
                y + 4f,
                gridPaint.apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 32f
                    textAlign = android.graphics.Paint.Align.RIGHT
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT,
                        android.graphics.Typeface.NORMAL
                    )
                }
            )
        }

        // 2) Axes X et Y (lignes épaisses, bouts arrondis)
        drawLine(
            color = Color(0xFF333333),
            start = Offset(marginLeft, marginTop),
            end = Offset(marginLeft, canvasHeight - marginBottom),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF333333),
            start = Offset(marginLeft, canvasHeight - marginBottom),
            end = Offset(canvasWidth - marginRight, canvasHeight - marginBottom),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )

        // 3) Calcul des points de la courbe
        val pointCount = values.size
        val gapX = if (pointCount > 1) plotWidth / (pointCount - 1) else plotWidth
        val scaleY = plotHeight / scaleFactor

        // Liste coordonées X,Y de la courbe
        val coords = values.mapIndexed { index, value ->
            val x = marginLeft + gapX * index
            val y = canvasHeight - marginBottom - value * scaleY
            Offset(x, y)
        }

        // 4) Construction du Path de la zone sous la courbe (area chart)
        val areaPath = androidx.compose.ui.graphics.Path().apply {
            if (coords.isNotEmpty()) {
                // 4.1) Démarrer au bas du premier point
                moveTo(coords[0].x, canvasHeight - marginBottom)

                // 4.2) Remonter jusqu'au premier point de la courbe
                lineTo(coords[0].x, coords[0].y)

                // 4.3) Ajouter tous les points intermédiaires
                for (i in 1 until coords.size) {
                    // Pour chaque segment, on peut lisser avec un simple Bézier au point médian
                    val prev = coords[i - 1]
                    val curr = coords[i]
                    val midX = (prev.x + curr.x) / 2
                    val midY = (prev.y + curr.y) / 2

                    quadraticBezierTo(
                        x1 = prev.x, y1 = prev.y,
                        x2 = midX, y2 = midY
                    )
                    quadraticBezierTo(
                        x1 = curr.x, y1 = curr.y,
                        x2 = curr.x, y2 = curr.y
                    )
                }

                // 4.4) Redescendre au fond sous le dernier point
                lineTo(coords.last().x, canvasHeight - marginBottom)

                // 4.5) Fermer le polygone (retour au point de départ, bas du premier point)
                close()
            }
        }

        // 5) Remplissage de la zone (dégradé vertical)
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFBBDEFB), Color.Transparent),
                startY = marginTop,
                endY = canvasHeight - marginBottom
            )
        )

        // 6) Tracé de la courbe lissée par-dessus (ligne bleue ou rouge)
        val linePath = androidx.compose.ui.graphics.Path().apply {
            if (coords.isNotEmpty()) {
                // 6.1) Départ : premier point
                moveTo(coords[0].x, coords[0].y)

                // 6.2) Bézier pour tous les segments
                for (i in 1 until coords.size) {
                    val prev = coords[i - 1]
                    val curr = coords[i]
                    val midX = (prev.x + curr.x) / 2
                    val midY = (prev.y + curr.y) / 2

                    quadraticBezierTo(
                        x1 = prev.x, y1 = prev.y,
                        x2 = midX, y2 = midY
                    )
                    quadraticBezierTo(
                        x1 = curr.x, y1 = curr.y,
                        x2 = curr.x, y2 = curr.y
                    )
                }
            }
        }

        drawPath(
            path = linePath,
            color = if (title == "CO2") Color(0xFF1976D2) else Color(0xFFD32F2F),
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )

        // 7) Points (petits carrés, ici on peut soit remplir, soit tracer le contour)
        coords.forEach { point ->
            drawRect(
                color = if (title == "CO2") Color(0xFF1976D2) else Color(0xFFD32F2F),
                topLeft = Offset(point.x - 6f, point.y - 6f),
                size = androidx.compose.ui.geometry.Size(12f, 12f),
                style = Stroke(width = 2f) // contour carré de 2px
            )
        }

        // 8) Titre et dernière valeur
        drawContext.canvas.nativeCanvas.apply {
            // 8.1) Titre en haut à gauche
            val titlePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 44f
                isAntiAlias = true
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
                )
                textAlign = android.graphics.Paint.Align.LEFT
            }
            drawText(
                title,
                marginLeft,
                marginTop - 12f,
                titlePaint
            )

            // 8.2) Dernière valeur en haut à droite
            val valPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 36f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.RIGHT
            }
            drawText(
                "${values.last()}",
                canvasWidth - marginRight,
                marginTop - 12f,
                valPaint
            )
        }
    }
}
