package com.hmgchallenge.app.sdk

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.tts.TextToSpeech
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ai.pleos.playground.stt.constant.ErrorType as SttErrorType
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.tts.constant.ErrorType as TtsErrorType
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener

/**
 * [debug 빌드 전용] Pleos SDK 4종(NaviHelper·FusedLocation·TTS·STT)이 이 기기에서 연결되는지 초기화만 해 본다.
 * release에는 같은 이름의 빈 구현(src/release)이 들어가 SDK 호출 코드가 제출 APK에 남지 않는다.
 * 기능 호출은 하지 않는다. 사용법·권한은 docs/guides/pleos-sdk.md.
 * - 공모전에서는 매니페스트 권한 선언만으로 쓸 수 있다(docs/contest/rules.md 1장).
 * - TTS·STT는 클라이언트 ID·시크릿이 필요 없는 ON_DEVICE 모드로 확인한다.
 * - 콜백이 오지 않는 SDK가 있어 TIMEOUT_MS 뒤에는 "응답 없음"으로 표시한다.
 */
class PleosSdkCheck(private val context: Context, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(PleosSdk.entries.associateWith<PleosSdk, SdkState> { SdkState.Checking })
    val state: StateFlow<Map<PleosSdk, SdkState>> = _state.asStateFlow()

    private var navi: NaviHelper? = null
    private var location: FusedLocation? = null
    private var tts: TextToSpeech? = null
    private var stt: SpeechToText? = null

    fun start() {
        guard(PleosSdk.NAVI) {
            navi = NaviHelper(context).also {
                it.initialize(
                    { set(PleosSdk.NAVI, SdkState.Ready) },
                    { e -> set(PleosSdk.NAVI, SdkState.Failed(e.message ?: e.javaClass.simpleName)) },
                )
            }
        }
        guard(PleosSdk.LOCATION) {
            val fused = FusedLocation(context)
            location = fused
            if (!fused.isSupported()) {
                set(PleosSdk.LOCATION, SdkState.Failed("이 차량에서 지원하지 않음"))
            } else {
                // FusedLocation.initialize()는 결과 콜백이 없다(pleos-sdk.md). 호출까지만 확인하고 "연결됨"으로 표시하지 않는다.
                fused.initialize()
                set(PleosSdk.LOCATION, SdkState.Initialized)
            }
        }
        guard(PleosSdk.TTS) {
            tts = TextToSpeech(context, TtsMode.ON_DEVICE).also {
                it.initialize(object : TtsInitListener {
                    override fun onSuccess() = set(PleosSdk.TTS, SdkState.Ready)
                    override fun onFailure(errorType: TtsErrorType) = set(PleosSdk.TTS, SdkState.Failed(errorType.name))
                })
            }
        }
        guard(PleosSdk.STT) {
            stt = SpeechToText(context, SttMode.ON_DEVICE).also {
                it.initialize(object : SttInitListener {
                    override fun onSuccess() = set(PleosSdk.STT, SdkState.Ready)
                    override fun onFailure(errorType: SttErrorType) = set(PleosSdk.STT, SdkState.Failed(errorType.name))
                })
            }
        }
        scope.launch {
            delay(TIMEOUT_MS)
            _state.update { all -> all.mapValues { (_, s) -> if (s == SdkState.Checking) SdkState.Failed("응답 없음") else s } }
        }
    }

    fun release() {
        runCatching { navi?.release() }.onFailure { Log.w(TAG, "NaviHelper release failed", it) }
        runCatching { location?.release() }.onFailure { Log.w(TAG, "FusedLocation release failed", it) }
        runCatching { tts?.release() }.onFailure { Log.w(TAG, "TextToSpeech release failed", it) }
        runCatching { stt?.release() }.onFailure { Log.w(TAG, "SpeechToText release failed", it) }
        navi = null; location = null; tts = null; stt = null
    }

    // SDK 서비스가 없는 기기·권한 오류에서도 앱이 죽지 않게 한다.
    private inline fun guard(sdk: PleosSdk, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            Log.w(TAG, "${sdk.label} initialize failed", e)
            set(sdk, SdkState.Failed(e.message ?: e.javaClass.simpleName))
        }
    }

    private fun set(sdk: PleosSdk, s: SdkState) {
        Log.i(TAG, "${sdk.label}: $s")
        _state.update { it + (sdk to s) }
    }

    private companion object {
        const val TAG = "HmgApp"
        const val TIMEOUT_MS = 10_000L
    }
}
