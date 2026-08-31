package net.hiroki.shizukubatterychecker

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

// --- データモデル & 画面状態 ---

data class BatteryHealthInfo(
    val model: String = "",
    val brand: String = "",
    val designCapacityMah: Int? = null,
    val currentCapacityMah: Int? = null,
    val healthPercentage: Float? = null,
    val wearPercentage: Float? = null,
    val level: String = "",
    val nodeUsed: String = ""
)

sealed interface UiState {
    object Initial : UiState
    object ShizukuNotRunning : UiState
    object PermissionRequired : UiState
    object Loading : UiState
    data class Success(val data: BatteryHealthInfo) : UiState
    data class Error(val message: String) : UiState
}

// --- ViewModel ---

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState

    private val prefs = application.getSharedPreferences("battery_checker_prefs", Context.MODE_PRIVATE)

    // 手動入力用の設計容量ステート
    var manualDesignInput by mutableStateOf("")

    private val onRequestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                loadBatteryData()
            } else {
                _uiState.value = UiState.PermissionRequired
            }
        }

    init {
        Shizuku.addRequestPermissionResultListener(onRequestPermissionResultListener)
        // 保存されている設計容量をロード
        val savedDesign = prefs.getInt("saved_design_capacity", -1)
        if (savedDesign != -1) {
            manualDesignInput = savedDesign.toString()
        }
    }

    override fun onCleared() {
        super.onCleared()
        Shizuku.removeRequestPermissionResultListener(onRequestPermissionResultListener)
    }

    fun checkAndLoad() {
        if (!Shizuku.pingBinder()) {
            _uiState.value = UiState.ShizukuNotRunning
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            loadBatteryData()
        } else {
            _uiState.value = UiState.PermissionRequired
        }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder()) {
            Shizuku.requestPermission(1001)
        }
    }

    /**
     * 手動入力された設計容量を適用し、SharedPreferencesに保存する
     */
    fun applyManualCapacity() {
        val value = manualDesignInput.toIntOrNull()
        prefs.edit {
            if (value != null && value > 0) {
                putInt("saved_design_capacity", value)
            } else {
                remove("saved_design_capacity")
            }
        }
        loadBatteryData()
    }

    fun loadBatteryData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val info = withContext(Dispatchers.IO) {
                    val baseInfo = BatteryRepository.fetchBatteryHealth()
                    
                    // 保存/入力されている値を優先して計算に使用する
                    val manualValue = manualDesignInput.toIntOrNull()
                    if (manualValue != null && baseInfo.currentCapacityMah != null && manualValue > 0) {
                        val health = (baseInfo.currentCapacityMah.toFloat() / manualValue.toFloat()) * 100f
                        baseInfo.copy(
                            designCapacityMah = manualValue,
                            healthPercentage = health,
                            wearPercentage = 100f - health
                        )
                    } else if (manualValue != null) {
                        baseInfo.copy(designCapacityMah = manualValue)
                    } else {
                        baseInfo
                    }
                }
                _uiState.value = UiState.Success(info)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "データ取得エラー")
            }
        }
    }
}

// --- Shizuku実行ユーティリティ ---

object ShizukuRunner {
    fun exec(command: String): String {
        if (!Shizuku.pingBinder()) return ""
        return runCatching {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            process.destroy()
            output.toString().trim()
        }.getOrDefault("")
    }
}

// --- バッテリー解析リポジトリ ---

object BatteryRepository {
    fun fetchBatteryHealth(): BatteryHealthInfo {
        val model = ShizukuRunner.exec("getprop ro.product.model")
        val brand = ShizukuRunner.exec("getprop ro.product.brand")

        // ① batteryディレクトリを探す
        val psList = ShizukuRunner.exec("ls /sys/class/power_supply/").split(Regex("\\s+")).filter { it.isNotBlank() }
        
        val targetNodeName = psList.find { it == "battery" } 
            ?: psList.find { it.contains("battery", ignoreCase = true) }
            ?: psList.firstOrNull()

        var designMah: Int? = null
        var currentMah: Int? = null
        var nodeUsed = ""

        if (targetNodeName != null) {
            nodeUsed = "/sys/class/power_supply/$targetNodeName"
            designMah = parseCapacity(ShizukuRunner.exec("cat $nodeUsed/charge_full_design"))
            currentMah = parseCapacity(ShizukuRunner.exec("cat $nodeUsed/charge_full"))
        }

        // フォールバック: sysfsで取れない場合は dumpsys を試す
        if (designMah == null) {
            val dumpsysPower = ShizukuRunner.exec("dumpsys power")
            val match = Regex("""mBatteryCapacity=(\d+)""").find(dumpsysPower)
            designMah = match?.groupValues?.get(1)?.toIntOrNull()
        }

        if (currentMah == null) {
            val dumpsysStats = ShizukuRunner.exec("dumpsys batterystats")
            val lastLearned = Regex("""Last learned battery capacity:\s*(\d+)""", RegexOption.IGNORE_CASE).find(dumpsysStats)
            val estimated = Regex("""Estimated battery capacity:\s*(\d+)""", RegexOption.IGNORE_CASE).find(dumpsysStats)
            currentMah = (lastLearned ?: estimated)?.groupValues?.get(1)?.toIntOrNull()
        }

        // リアルタイム情報取得
        val batterySys = ShizukuRunner.exec("dumpsys battery")
        val level = Regex("""level:\s*(\d+)""").find(batterySys)?.groupValues?.get(1) ?: "?"

        var health: Float? = null
        var wear: Float? = null
        if (designMah != null && currentMah != null && designMah > 0) {
            health = (currentMah.toFloat() / designMah.toFloat()) * 100f
            wear = 100f - health
        }

        return BatteryHealthInfo(
            model = model,
            brand = brand,
            designCapacityMah = designMah,
            currentCapacityMah = currentMah,
            healthPercentage = health,
            wearPercentage = wear,
            level = level,
            nodeUsed = nodeUsed
        )
    }

    private fun parseCapacity(raw: String): Int? {
        val v = raw.trim().toIntOrNull() ?: return null
        return when {
            // µAh -> mAh (÷ 1000)
            v > 100000 -> v / 1000 
            v > 500 -> v
            else -> null
        }
    }
}

// --- UI (Jetpack Compose) ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = ViewModelProvider(
            this, 
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[MainViewModel::class.java]

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BatteryScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun BatteryScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkAndLoad()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val state = uiState) {
            is UiState.ShizukuNotRunning -> {
                Text("❌ Shizukuが起動していません", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Shizukuアプリを開いてサービスを開始してください。")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.checkAndLoad() }) {
                    Text("再確認")
                }
            }
            is UiState.PermissionRequired -> {
                Text("🔒 権限が必要です", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Shizuku経由で端末情報を取得する許可が必要です。")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.requestPermission() }) {
                    Text("権限をリクエスト")
                }
            }
            is UiState.Loading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("バッテリー情報を解析中...")
            }
            is UiState.Success -> {
                val data = state.data
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("📱 ${data.brand} ${data.model}", style = MaterialTheme.typography.titleLarge)
                        if (data.nodeUsed.isNotEmpty()) {
                            Text("📁 Node: ${data.nodeUsed}", style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Text("🔋 残量: ${data.level}%")
                        Spacer(modifier = Modifier.height(16.dp))

                        data.healthPercentage?.let { health ->
                            val healthFormatted = String.format("%.1f", health)
                            Text("❤️ 健康度: $healthFormatted%", style = MaterialTheme.typography.headlineMedium)
                            LinearProgressIndicator(
                                progress = { (health / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .padding(vertical = 8.dp)
                            )
                        } ?: Text("⚠️ 健康度の自動計算に失敗しました")

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("📐 設計容量: ${data.designCapacityMah ?: "?"} mAh")
                        Text("📊 実効容量: ${data.currentCapacityMah ?: "?"} mAh")
                        data.wearPercentage?.let { wear ->
                            val wearFormatted = String.format("%.1f", wear)
                            Text("📉 推定劣化率: $wearFormatted%")
                        }

                        // 手動入力セクション
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("✏️ 設計容量を手動入力 (mAh):", style = MaterialTheme.typography.labelMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = viewModel.manualDesignInput,
                                onValueChange = { viewModel.manualDesignInput = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("例: 5000") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { viewModel.applyManualCapacity() }) {
                                Text("適用")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = { viewModel.loadBatteryData() }) {
                    Text("再更新")
                }
            }
            is UiState.Error -> {
                Text("❌ エラー: ${state.message}", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.checkAndLoad() }) {
                    Text("再試行")
                }
            }
            else -> {}
        }
    }
}
