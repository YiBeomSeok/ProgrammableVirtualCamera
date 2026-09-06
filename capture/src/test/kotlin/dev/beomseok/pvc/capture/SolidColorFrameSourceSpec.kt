package dev.beomseok.pvc.capture

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.webrtc.VideoFrame

private const val WIDTH = 1280
private const val HEIGHT = 720
private const val FRAME_RATE = 30
private val COLOR = YuvColor(y = 146u, u = 53u, v = 193u)

private fun source(buffers: I420Buffers) =
    SolidColorFrameSource(WIDTH, HEIGHT, FRAME_RATE, COLOR, buffers)

private fun ByteBuffer.distinctBytes(): Set<Byte> =
    (0 until capacity()).map { get(it) }.toSet()

@OptIn(ExperimentalCoroutinesApi::class)
class SolidColorFrameSourceSpec : StringSpec({

    "프레임 크기가 요청한 해상도와 같다" {
        runTest {
            val frame = source(FakeI420Buffers()).frames().take(1).toList().single()

            frame.buffer.width shouldBe WIDTH
            frame.buffer.height shouldBe HEIGHT
        }
    }

    "세 평면이 모두 지정한 색으로 채워진다" {
        runTest {
            val frame = source(FakeI420Buffers()).frames().take(1).toList().single()
            val buffer = frame.buffer as VideoFrame.I420Buffer

            buffer.dataY.distinctBytes() shouldBe setOf(COLOR.y.toByte())
            buffer.dataU.distinctBytes() shouldBe setOf(COLOR.u.toByte())
            buffer.dataV.distinctBytes() shouldBe setOf(COLOR.v.toByte())
        }
    }

    "31번째 프레임까지 정확히 1초가 걸린다" {
        runTest {
            val start = currentTime

            val frames = source(FakeI420Buffers()).frames().take(31).toList()

            frames shouldHaveSize 31
            currentTime - start shouldBe 1_000L
        }
    }

    "timestamp가 프레임 간격만큼 단조 증가한다" {
        runTest {
            val timestamps = source(FakeI420Buffers()).frames().take(4).toList().map { it.timestampNs }

            timestamps shouldBe listOf(0L, 33_333_333L, 66_666_666L, 100_000_000L)
            timestamps.zipWithNext().all { (earlier, later) -> earlier < later } shouldBe true
        }
    }

    "수집을 취소하면 프레임 생성이 멈춘다" {
        runTest {
            var received = 0
            val collecting = launch {
                source(FakeI420Buffers()).frames().collect {
                    received++
                    it.release()
                }
            }
            advanceTimeBy(1.seconds)

            collecting.cancelAndJoin()
            val receivedUntilCancel = received
            advanceTimeBy(1.seconds)

            received shouldBe receivedUntilCancel
        }
    }

    "프레임마다 버퍼 참조를 하나씩 넘긴다" {
        runTest {
            val buffers = FakeI420Buffers()

            val frames = source(buffers).frames().take(3).toList()

            // take가 흐름을 끝내며 소스 몫은 이미 풀렸다. 남은 셋은 프레임이 쥐고 있다.
            buffers.allocated.single().refCount shouldBe 3
            frames.forEach { it.release() }
            buffers.allocated.single().refCount shouldBe 0
        }
    }

    "수집을 취소하면 소스가 쥔 버퍼까지 풀린다" {
        runTest {
            val buffers = FakeI420Buffers()
            val collecting = launch { source(buffers).frames().collect { it.release() } }
            advanceTimeBy(1.seconds)

            collecting.cancelAndJoin()

            buffers.allocated.single().refCount shouldBe 0
        }
    }
})

private class FakeI420Buffers : I420Buffers {
    val allocated = mutableListOf<FakeI420Buffer>()

    override fun allocate(width: Int, height: Int): VideoFrame.I420Buffer =
        FakeI420Buffer(width, height).also { allocated += it }
}

/** native 없이 도는 I420 버퍼. 평면은 stride 없이 딱 맞게 잡는다. */
private class FakeI420Buffer(
    private val frameWidth: Int,
    private val frameHeight: Int,
) : VideoFrame.I420Buffer {

    private val chromaWidth = (frameWidth + 1) / 2
    private val chromaHeight = (frameHeight + 1) / 2
    private val planeY = ByteBuffer.allocateDirect(frameWidth * frameHeight)
    private val planeU = ByteBuffer.allocateDirect(chromaWidth * chromaHeight)
    private val planeV = ByteBuffer.allocateDirect(chromaWidth * chromaHeight)

    var refCount = 1
        private set

    override fun getWidth(): Int = frameWidth
    override fun getHeight(): Int = frameHeight
    override fun getDataY(): ByteBuffer = planeY
    override fun getDataU(): ByteBuffer = planeU
    override fun getDataV(): ByteBuffer = planeV
    override fun getStrideY(): Int = frameWidth
    override fun getStrideU(): Int = chromaWidth
    override fun getStrideV(): Int = chromaWidth
    override fun toI420(): VideoFrame.I420Buffer = this
    override fun retain() { refCount++ }
    override fun release() { refCount-- }

    override fun cropAndScale(
        cropX: Int,
        cropY: Int,
        cropWidth: Int,
        cropHeight: Int,
        scaleWidth: Int,
        scaleHeight: Int,
    ): VideoFrame.Buffer = this
}
