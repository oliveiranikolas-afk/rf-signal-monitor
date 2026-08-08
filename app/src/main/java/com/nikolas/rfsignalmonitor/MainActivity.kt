package com.nikolas.rfsignalmonitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

private const val WINDOW_SIZE = 20
private const val VARIATION_THRESHOLD_DB = 3.0

class MainActivity : ComponentActivity() {

    private lateinit var wifiManager: WifiManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        setContent {
            MaterialTheme {
                RfMonitorScreen(wifiManager = wifiManager)
            }
        }
    }
}

data class RssiSample(val timestamp: Long, val rssi: Int)

@Composable
fun RfMonitorScreen(wifiManager: WifiManager) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    var monitoring by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<RssiSample>() }
    var currentRssi by remember { mutableStateOf<Int?>(null) }
    var movementFlag by remember { mutableStateOf(false) }
    var stdDev by remember { mutableStateOf(0.0) }
    val eventLog = remember { mutableStateListOf<String>() }
    var selectedTab by remember { mutableStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(monitoring, hasPermission) {
        if (!monitoring || !hasPermission) return@LaunchedEffect
        while (isActive && monitoring) {
            val info = wifiManager.connectionInfo
            val rssi = info?.rssi ?: -100

            currentRssi = rssi
            history.add(RssiSample(System.currentTimeMillis(), rssi))
            if (history.size > WINDOW_SIZE) history.removeAt(0)

            if (history.size >= 5) {
                val values = history.map { it.rssi.toDouble() }
                val mean = values.average()
                val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
                stdDev = sqrt(variance)

                val wasFlagged = movementFlag
                movementFlag = stdDev > VARIATION_THRESHOLD_DB

                if (movementFlag && !wasFlagged) {
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    eventLog.add(0, "$time — variação de sinal acima do limiar (desvio: ${"%.1f".format(stdDev)} dB)")
                    if (eventLog.size > 10) eventLog.removeAt(eventLog.lastIndex)
                }
            }

            webViewRef?.evaluateJavascript(
                "window.updateSignal && window.updateSignal($rssi, $stdDev, $movementFlag)",
                null
            )

            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E11))
            .padding(20.dp)
    ) {
        Text(
            "Monitor de Sinal RF",
            color = Color(0xFFE8EDF2),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Leitura real de RSSI — não detecta pessoas ou objetos",
            color = Color(0xFF8B95A1),
            fontSize = 12.sp
        )

        Spacer(Modifier.height(20.dp))

        if (!hasPermission) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF161D24))) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "É preciso conceder a permissão de localização para o Android liberar a leitura do RSSI do Wi-Fi.",
                        color = Color(0xFFC9D3DC),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }) {
                        Text("Conceder permissão")
                    }
                }
            }
            return@Column
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0F14))) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("RSSI atual", color = Color(0xFF8B95A1), fontSize = 12.sp)
                    Text(
                        currentRssi?.let { "$it dBm" } ?: "—",
                        color = Color(0xFF4EA1FF),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Desvio-padrão (janela de ${WINDOW_SIZE}s)", color = Color(0xFF8B95A1), fontSize = 12.sp)
                    Text("%.2f dB".format(stdDev), color = Color(0xFFC9D3DC), fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val badgeColor = if (movementFlag) Color(0xFFFFB020) else Color(0xFF39FF88)
                    Box(
                        Modifier
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            if (movementFlag) "VARIAÇÃO ACIMA DO LIMIAR" else "SINAL ESTÁVEL",
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF0A0F14),
            contentColor = Color(0xFF4EA1FF)
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Gráfico", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Visualização 3D", fontSize = 12.sp) }
            )
        }

        Spacer(Modifier.height(12.dp))

        if (selectedTab == 0) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0F14))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Histórico de RSSI", color = Color(0xFF8B95A1), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        if (history.size < 2) return@Canvas
                        val minV = -90f
                        val maxV = -30f
                        val stepX = size.width / (WINDOW_SIZE - 1).toFloat()
                        val points = history.mapIndexed { idx, sample ->
                            val x = idx * stepX
                            val normalized = ((sample.rssi - minV) / (maxV - minV)).coerceIn(0f, 1f)
                            val y = size.height - normalized * size.height
                            Offset(x, y)
                        }
                        for (i in 0 until points.size - 1) {
                            drawLine(
                                color = Color(0xFF4EA1FF),
                                start = points[i],
                                end = points[i + 1],
                                strokeWidth = 4f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0F14)),
                modifier = Modifier.fillMaxWidth()
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            webViewClient = WebViewClient()
                            loadUrl("file:///android_asset/rf_3d.html")
                            webViewRef = this
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { monitoring = !monitoring },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (monitoring) "Pausar monitoramento" else "Iniciar monitoramento")
        }

        Spacer(Modifier.height(16.dp))

        Text("Registro de variações", color = Color(0xFF8B95A1), fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Column {
            if (eventLog.isEmpty()) {
                Text("Nenhuma variação registrada ainda.", color = Color(0xFF3E4854), fontSize = 12.sp)
            } else {
                eventLog.forEach { line ->
                    Text(line, color = Color(0xFF8B95A1), fontSize = 11.sp)
                    Spacer(Modifier.height(3.dp))
                }
            }
        }
    }
}
