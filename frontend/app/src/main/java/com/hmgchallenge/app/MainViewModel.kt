package com.hmgchallenge.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hmgchallenge.app.driving.UxRestrictionsMonitor
import com.hmgchallenge.app.driving.UxRestrictionsState
import com.hmgchallenge.shared.HealthResponse
import com.hmgchallenge.app.care.AtomicCareRecordStore
import com.hmgchallenge.shared.care.CareEventType
import com.hmgchallenge.shared.care.CareRepository
import com.hmgchallenge.app.navigation.PleosNavigationClient
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.facility.StopCandidate
import com.hmgchallenge.shared.forecast.journeyForecasts
import com.hmgchallenge.shared.journey.NavigationRepository
import com.hmgchallenge.shared.journey.NavigationSource
import java.io.File
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Success<T>(val value: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}

data class MainUiState(
    val health: LoadState<HealthResponse> = LoadState.Loading,
    val battery: LoadState<Int> = LoadState.Loading,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    // 운전 중 UX 제한. ViewModel 수명 동안 구독한다(Vehicle SDK와 같은 방식).
    // 구독 비용이 작고, window 이동·포그라운드 복귀 직후에도 제한 상태가 바로 맞아야 하므로 STARTED 단위로 끊지 않는다.
    private val uxMonitor = UxRestrictionsMonitor(application)
    val uxRestrictions: StateFlow<UxRestrictionsState> = uxMonitor.state

    private val careRepository = CareRepository(AtomicCareRecordStore(File(application.filesDir, "care-record.json")))
    val careState = careRepository.state

    val navigation: NavigationSource = NavigationRepository({ PleosNavigationClient(application) })
    private val forecastTicks = MutableStateFlow(System.currentTimeMillis())
    val forecast = journeyForecasts(careRepository.records, navigation.snapshots, forecastTicks)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private var navigationJob: Job? = null

    // Vehicle SDK 인스턴스는 화면(ViewModel) 수명 동안 유지하고 onCleared()에서 release 한다.
    private var vehicle: Vehicle? = null

    // init에서 등록하므로 init보다 먼저 선언해야 한다.
    private val batteryListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(percent: Int?) = onBatteryUpdated(percent)

        override fun onFailed(e: Exception) {
            Log.w(TAG, "EvBatteryStateOfCharge listener failed", e)
        }
    }

    init {
        uxMonitor.start()
        reloadCareRecords()
        refreshHealth()
        initVehicleAndReadBattery()
    }

    fun reloadCareRecords() {
        viewModelScope.launch { careRepository.load() }
    }

    fun startNavigation() {
        if (navigationJob?.isActive == true) return
        navigation.start()
        forecastTicks.value = System.currentTimeMillis()
        navigationJob = viewModelScope.launch {
            var seconds = 0
            while (isActive) {
                delay(1_000)
                navigation.tick()
                seconds++
                if (seconds % 30 == 0) {
                    forecastTicks.value = System.currentTimeMillis()
                    if (navigation.connection.value == DataStatus.READY) navigation.refresh()
                }
            }
        }
    }

    fun stopNavigation() {
        navigationJob?.cancel()
        navigationJob = null
        navigation.stop()
    }

    fun refreshNavigation() = navigation.refresh()
    fun prepareStopNavigation(candidate: StopCandidate) = navigation.prepareStop(candidate)
    fun confirmStopNavigation(confirmationId: String) = navigation.confirmStop(confirmationId)
    fun cancelStopNavigation() = navigation.cancelStop()

    fun recordCare(type: CareEventType) {
        viewModelScope.launch { careRepository.record(type) }
    }

    fun undoCare() {
        viewModelScope.launch { careRepository.undoLatest() }
    }

    fun saveCareProfile(monthAge: Int, preferredAmountMl: Int?) {
        viewModelScope.launch {
            // Check again when the action executes: driving may begin while the form is open.
            val ux = uxRestrictions.value
            if (ux.requiresDistractionOptimization || ux.noSetup) {
                careRepository.requireParking()
                return@launch
            }
            careRepository.saveProfile(monthAge, preferredAmountMl)
        }
    }

    fun editCareEvent(id: String, atEpochMillis: Long, amountMl: Int?) {
        viewModelScope.launch {
            val ux = uxRestrictions.value
            if (ux.requiresDistractionOptimization || ux.noSetup) {
                careRepository.requireParking()
                return@launch
            }
            careRepository.editEvent(id, atEpochMillis, amountMl)
        }
    }

    fun refreshHealth() {
        _state.update { it.copy(health = LoadState.Loading) }
        viewModelScope.launch {
            val result = runCatching { fetchHealth() }
            _state.update {
                it.copy(
                    health = result.fold(
                        onSuccess = { body -> LoadState.Success(body) },
                        onFailure = { e ->
                            Log.w(TAG, "health check failed", e)
                            LoadState.Error("${e.javaClass.simpleName}: ${e.message ?: "알 수 없는 오류"}")
                        },
                    ),
                )
            }
        }
    }

    fun refreshBattery() {
        _state.update { it.copy(battery = LoadState.Loading) }
        readBattery(retriesLeft = 0)
    }

    private suspend fun fetchHealth(): HealthResponse = withContext(Dispatchers.IO) {
        val url = URL(BuildConfig.BACKEND_BASE_URL + HealthResponse.PATH)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 3_000
            readTimeout = 3_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            check(code == HttpURLConnection.HTTP_OK) { "HTTP $code" }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString<HealthResponse>(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun initVehicleAndReadBattery() {
        try {
            vehicle = Vehicle(getApplication()).also { it.initialize() }
        } catch (e: Throwable) {
            // SDK 서비스가 없는 기기 등에서도 앱이 죽지 않게 한다.
            Log.e(TAG, "Vehicle initialize failed", e)
            _state.update { it.copy(battery = LoadState.Error("Vehicle SDK 초기화 실패: ${e.message ?: e.javaClass.simpleName}")) }
            return
        }
        // initialize()는 서비스 바인딩을 비동기로 진행할 수 있어, 첫 호출이 실패하면 한 번 더 시도한다.
        readBattery(retriesLeft = 1)
        registerBatteryListener()
    }

    private fun readBattery(retriesLeft: Int) {
        val v = vehicle ?: run {
            _state.update { it.copy(battery = LoadState.Error("Vehicle SDK가 초기화되지 않았습니다")) }
            return
        }
        try {
            v.getEvBattery().getEvBatteryStateOfCharge(
                { percent: Int? -> onBatteryUpdated(percent) },
                { e: Exception ->
                    Log.w(TAG, "getEvBatteryStateOfCharge failed (retriesLeft=$retriesLeft)", e)
                    if (retriesLeft > 0) {
                        viewModelScope.launch {
                            delay(1_500)
                            readBattery(retriesLeft - 1)
                        }
                    } else {
                        _state.update { it.copy(battery = LoadState.Error("배터리 잔량 조회 실패: ${e.message ?: e.javaClass.simpleName}")) }
                    }
                },
            )
        } catch (e: Throwable) {
            Log.e(TAG, "getEvBatteryStateOfCharge threw", e)
            _state.update { it.copy(battery = LoadState.Error("배터리 잔량 조회 실패: ${e.message ?: e.javaClass.simpleName}")) }
        }
    }

    // Virtual Console 등에서 값이 바뀌면 화면에 바로 반영되도록 구독한다.
    private fun registerBatteryListener() {
        try {
            vehicle?.getEvBattery()?.registerEvBatteryStateOfCharge(batteryListener)
        } catch (e: Throwable) {
            Log.w(TAG, "registerEvBatteryStateOfCharge failed", e)
        }
    }

    private fun onBatteryUpdated(percent: Int?) {
        _state.update {
            it.copy(battery = if (percent == null) LoadState.Error("배터리 잔량 값이 비어 있습니다") else LoadState.Success(percent))
        }
    }

    override fun onCleared() {
        stopNavigation()
        uxMonitor.stop()
        try {
            vehicle?.getEvBattery()?.unregisterEvBatteryStateOfCharge(batteryListener)
        } catch (e: Throwable) {
            Log.w(TAG, "unregisterEvBatteryStateOfCharge failed", e)
        }
        try {
            vehicle?.release()
        } catch (e: Throwable) {
            Log.w(TAG, "Vehicle release failed", e)
        }
        vehicle = null
        super.onCleared()
    }

    private companion object {
        const val TAG = "HmgApp"
    }
}
