package dev.beomseok.pvc.capture

import java.nio.ByteBuffer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TimeSource
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
 * 버퍼 하나를 모든 프레임이 나눠 쓰는 단색 소스.
 * 프레임은 수집하는 동안만 빌려주며, 보관하려는 쪽만 retain하고 나중에 release한다.
 */
class SolidColorFrameSource(
    private val width: Int,
    private val height: Int,
    private val frameRate: Int,
    private val color: YuvColor,
    private val buffers: I420Buffers = NativeI420Buffers,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : FrameSource {

    init {
        require(frameRate > 0) { "frameRate는 양수여야 한다: $frameRate" }
    }

    override fun frames(): Flow<VideoFrame> = flow {
        val buffer = buffers.allocate(width, height)
        try {
            fill(buffer)
            val start = timeSource.markNow()
            var index = 0L
            while (true) {
                emit(VideoFrame(buffer, 0, frameTime(index).inWholeNanoseconds))

                // 잔 시간이 아니라 실제 경과 시간에서 다음 목표까지를 뺀다.
                // 수집자의 처리 시간이 페이싱에 얹히지 않게 하려는 것이다.
                index = nextIndex(index, start.elapsedNow())
                val wait = frameTime(index) - start.elapsedNow()
                if (wait > Duration.ZERO) delay(wait)
            }
        } finally {
            buffer.release()
        }
    }

    /**
     * 다음에 내보낼 프레임의 번호.
     * 밀렸으면 만들지 못한 프레임을 몰아 내보내지 않고 다음 경계로 건너뛴다.
     */
    private fun nextIndex(current: Long, elapsed: Duration): Long {
        val passed = elapsed.inWholeNanoseconds * frameRate / NANOS_PER_SECOND
        return maxOf(current, passed) + 1
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
