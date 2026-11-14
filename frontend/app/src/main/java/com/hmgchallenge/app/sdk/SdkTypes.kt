package com.hmgchallenge.app.sdk

/** SDK 연결 확인 결과 형식. 확인 로직은 debug 빌드(src/debug)에만 있다. */
enum class PleosSdk(val label: String) {
    NAVI("NaviHelper"),
    LOCATION("FusedLocation"),
    TTS("TextToSpeech"),
    STT("SpeechToText"),
}

sealed interface SdkState {
    data object Checking : SdkState
    data object Ready : SdkState
    /** 초기화를 호출했지만 결과 콜백이 없는 SDK(FusedLocation). 연결 여부는 확인하지 않았다. */
    data object Initialized : SdkState
    data class Failed(val reason: String) : SdkState
}
