package com.hmgchallenge.app.sdk

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * release(제출) 빌드용 빈 구현. SDK 연결 확인은 개발용이라 debug 빌드(src/debug)에만 있다.
 * 제출 앱은 FusedLocation·TTS·STT를 쓰지 않고 권한도 선언하지 않으므로, 그 API를 부르는 코드를 넣지 않는다.
 */
@Suppress("UNUSED_PARAMETER")
class PleosSdkCheck(context: Context, scope: CoroutineScope) {
    val state: StateFlow<Map<PleosSdk, SdkState>> = MutableStateFlow(emptyMap<PleosSdk, SdkState>()).asStateFlow()

    fun start() = Unit

    fun release() = Unit
}
