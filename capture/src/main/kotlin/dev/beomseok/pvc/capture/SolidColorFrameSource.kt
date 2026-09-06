package dev.beomseok.pvc.capture

import java.nio.ByteBuffer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.webrtc.JavaI420Buffer
import org.webrtc.VideoFrame

private const val NANOS_PER_SECOND = 1_000_000_000L

/** I420 세 평면을 각각 채울 값. */
data class YuvColor(val y: UByte, val u: UByte, val v: UByte)

/**
 * I420 버퍼를 만든다.
 * native 라이브러리 없이 도는 단위 테스트가 갈아끼울 자리다.
 */
interface I420Buffers {
    fun allocate(width: Int, height: Int): VideoFrame.I420Buffer
}

object NativeI420Buffers : I420Buffers {
    override fun allocate(width: Int, height: Int): VideoFrame.I420Buffer =
        JavaI420Buffer.allocate(width, height)
}

/**
 * 버퍼 하나를 프레임마다 참조만 늘려 나눠 주는 단색 소스.
 * 내보낸 프레임의 release는 수집하는 쪽 책임이다.
 */
class SolidColorFrameSource(
    private val width: Int,
    private val height: Int,
    private val frameRate: Int,
    private val color: YuvColor,
    private val buffers: I420Buffers = NativeI420Buffers,
) : FrameSource {

    init {
        require(frameRate > 0) { "frameRate는 양수여야 한다: $frameRate" }
    }

    override fun frames(): Flow<VideoFrame> = flow {
        val buffer = buffers.allocate(width, height)
        fill(buffer)
        try {
            var index = 0L
            var slept = Duration.ZERO
            while (true) {
                buffer.retain()
                emit(VideoFrame(buffer, 0, frameTime(index).inWholeNanoseconds))

                index++
                // delay는 ns를 ms로 올림한다. 한 프레임 간격이 정수 ms가 아니면
                // 매번 올림돼 느려지므로, 목표를 ms로 스냅하고 잔 만큼만 누적한다.
                val target = frameTime(index).inWholeMilliseconds.milliseconds
                delay(target - slept)
                slept = target
            }
        } finally {
            buffer.release()
        }
    }

    /** 시작부터 [index]번째 프레임까지의 시각. timestamp와 페이싱이 같이 쓴다. */
    private fun frameTime(index: Long): Duration =
        (index * NANOS_PER_SECOND / frameRate).nanoseconds

    private fun fill(buffer: VideoFrame.I420Buffer) {
        val chromaWidth = (width + 1) / 2
        val chromaHeight = (height + 1) / 2
        buffer.dataY.fillRows(buffer.strideY, width, height, color.y)
        buffer.dataU.fillRows(buffer.strideU, chromaWidth, chromaHeight, color.u)
        buffer.dataV.fillRows(buffer.strideV, chromaWidth, chromaHeight, color.v)
    }

    private fun ByteBuffer.fillRows(stride: Int, rowBytes: Int, rows: Int, value: UByte) {
        val row = ByteArray(rowBytes) { value.toByte() }
        for (y in 0 until rows) {
            position(y * stride)
            put(row)
        }
        rewind()
    }
}
