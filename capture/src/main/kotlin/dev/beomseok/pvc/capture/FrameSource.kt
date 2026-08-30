package dev.beomseok.pvc.capture

import kotlinx.coroutines.flow.Flow
import org.webrtc.VideoFrame

/**
 * 영상 프레임의 공급원.
 *
 * 프레임을 밀어 넣는 대신 [Flow]를 돌려준다. 수집을 멈추면 취소가 전파되고,
 * 구현체가 잡고 있던 자원은 자신이 연 자리에서 해제된다. 호출자가 정리 순서를
 * 신경 쓸 필요가 없다.
 *
 * Phase 2에서 구현체가 JNI 뒤로 옮겨가도 이 시그니처는 바뀌지 않는다.
 */
interface FrameSource {
    fun frames(): Flow<VideoFrame>
}

/**
 * 프레임 흐름에 끼어드는 처리 단계.
 *
 * Phase 2의 native 처리가 들어올 자리다. 소스와 소비자를 건드리지 않고
 * 중간에만 꽂을 수 있도록 Flow에서 Flow로 가는 모양으로 둔다.
 */
interface FrameTransform {
    fun apply(upstream: Flow<VideoFrame>): Flow<VideoFrame>
}
