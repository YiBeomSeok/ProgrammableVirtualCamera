package dev.beomseok.pvc.capture

import android.content.Context
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory

/**
 * 프로세스당 한 번만 해야 하는 WebRTC 전역 초기화.
 *
 * 호출 횟수를 세는 대신 [android.app.Application.onCreate]에서만 부른다.
 * 플랫폼이 프로세스당 한 번을 보장하므로 우리 쪽에 확인할 상태가 남지 않는다.
 */
fun initializeWebRtc(context: Context) {
    PeerConnectionFactory.initialize(
        PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
            .createInitializationOptions(),
    )
}

/**
 * [PeerConnectionFactory]와 [EglBase]의 수명을 [block]에 묶는다.
 *
 * 둘 다 native 자원을 들고 있어서 해제 시점이 분명해야 한다. 만드는 자리와
 * 버리는 자리를 한 함수에 두면 호출자는 "지금 해제해도 되나"를 판단하지 않는다.
 * 블록이 예외로 끝나든 취소되든 해제는 같은 자리에서 일어난다.
 */
inline fun <T> withWebRtc(block: (PeerConnectionFactory, EglBase) -> T): T {
    val eglBase = EglBase.create()
    try {
        val factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
        try {
            return block(factory, eglBase)
        } finally {
            factory.dispose()
        }
    } finally {
        eglBase.release()
    }
}
