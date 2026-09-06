package dev.beomseok.pvc.capture

import java.nio.ByteBuffer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.webrtc.JavaI420Buffer
import org.webrtc.VideoFrame

private const val NANOS_PER_SECOND = 1_000_000_000L
private const val MILLIS_PER_SECOND = 1_000L

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
 * 같은 색으로만 채운 프레임을 정해진 fps로 만든다.
 * 내용이 바뀌지 않으므로 버퍼 하나를 프레임마다 참조만 늘려 나눠 쓴다.
 * 내보낸 프레임을 release하는 것은 수집하는 쪽 책임이다.
 */
class SolidColorFrameSource(
    private val width: Int,
    private val height: Int,
    private val frameRate: Int,
    private val color: YuvColor,
    private val buffers: I420Buffers = NativeI420Buffers,
) : FrameSource {

    override fun frames(): Flow<VideoFrame> = flow {
        val buffer = buffers.allocate(width, height)
        fill(buffer)
        try {
            var index = 0L
            var elapsedMillis = 0L
            while (true) {
                buffer.retain()
                emit(VideoFrame(buffer, 0, index * NANOS_PER_SECOND / frameRate))

                index++
                // 30fps의 한 프레임은 33.333ms라 매번 같은 정수 ms를 쉬면 밀린다.
                // 목표 시각을 누적해서 그 오차를 없앤다.
                val targetMillis = index * MILLIS_PER_SECOND / frameRate
                delay(targetMillis - elapsedMillis)
                elapsedMillis = targetMillis
            }
        } finally {
            buffer.release()
        }
    }

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
