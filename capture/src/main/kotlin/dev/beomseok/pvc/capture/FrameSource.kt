package dev.beomseok.pvc.capture

import kotlinx.coroutines.flow.Flow
import org.webrtc.VideoFrame

/**
 * 영상 프레임의 공급원.
 * 수집을 멈추면 취소가 전파되어 구현체가 연 자원이 그 자리에서 해제된다.
 */
interface FrameSource {
    fun frames(): Flow<VideoFrame>
}

/**
 * 프레임 흐름에 끼어드는 처리 단계.
 * 소스와 소비자를 건드리지 않고 중간에만 꽂도록 Flow에서 Flow로 간다.
 */
interface FrameTransform {
    fun apply(upstream: Flow<VideoFrame>): Flow<VideoFrame>
}
