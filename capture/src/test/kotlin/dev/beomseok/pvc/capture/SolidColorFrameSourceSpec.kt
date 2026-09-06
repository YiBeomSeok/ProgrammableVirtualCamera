package dev.beomseok.pvc.capture

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import kotlin.time.AbstractLongTimeSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.webrtc.VideoFrame

private const val WIDTH = 1280
private const val HEIGHT = 720
private const val FRAME_RATE = 30
private val FRAME_RATES = listOf(30, 45, 60)
private val COLOR = YuvColor(y = 146u, u = 53u, v = 193u)
private val PROCESSING = 5.milliseconds

private fun source(
    buffers: I420Buffers,
    frameRate: Int = FRAME_RATE,
    timeSource: TimeSource = TimeSource.Monotonic,
) = SolidColorFrameSource(WIDTH, HEIGHT, frameRate, COLOR, buffers, timeSource)

private fun ByteBuffer.distinctBytes(): Set<Byte> =
    (0 until capacity()).map { get(it) }.toSet()

/** 페이싱 검증이 실제 시계 대신 테스트 스케줄러의 가상 시간을 보게 한다. */
@OptIn(ExperimentalCoroutinesApi::class)
private class SchedulerTimeSource(private val scheduler: TestCoroutineScheduler) :
    AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
    override fun read(): Long = scheduler.currentTime
}

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
            val buffer = source(FakeI420Buffers()).frames().take(1).toList()
                .single().buffer as VideoFrame.I420Buffer

            buffer.dataY.distinctBytes() shouldBe setOf(COLOR.y.toByte())
            buffer.dataU.distinctBytes() shouldBe setOf(COLOR.u.toByte())
            buffer.dataV.distinctBytes() shouldBe setOf(COLOR.v.toByte())
        }
    }

    "timestamp가 프레임 간격만큼 단조 증가한다" {
        runTest {
            val timestamps = source(FakeI420Buffers()).frames().take(4).toList()
                .map { it.timestampNs.nanoseconds }

            timestamps shouldBe listOf(
                Duration.ZERO,
                33_333_333.nanoseconds,
                66_666_666.nanoseconds,
                100_000_000.nanoseconds,
            )
        }
    }

    "frameRate가 0 이하면 소스를 만들 수 없다" {
        shouldThrow<IllegalArgumentException> { source(FakeI420Buffers(), 0) }
        shouldThrow<IllegalArgumentException> { source(FakeI420Buffers(), -1) }
    }

    FRAME_RATES.forEach { frameRate ->
        "${frameRate}fps는 처리 시간이 있어도 ${frameRate}프레임을 1초 안에 채운다" {
            runTest {
                val arrivals = mutableListOf<Long>()

                source(FakeI420Buffers(), frameRate, SchedulerTimeSource(testScheduler))
                    .frames()
                    .take(frameRate + 1)
                    .collect {
                        arrivals += currentTime
                        delay(PROCESSING)
                    }

                arrivals shouldHaveSize (frameRate + 1)
                arrivals.first() shouldBe 0L
                arrivals.last() shouldBe 1_000L
            }
        }
    }

    "처리 시간이 프레임 간격보다 길면 밀린 프레임을 몰아 내보내지 않는다" {
        runTest {
            val slow = 100.milliseconds
            val arrivals = mutableListOf<Long>()
            val timestamps = mutableListOf<Duration>()

            source(FakeI420Buffers(), FRAME_RATE, SchedulerTimeSource(testScheduler))
                .frames()
                .take(5)
                .collect {
                    arrivals += currentTime
                    timestamps += it.timestampNs.nanoseconds
                    delay(slow)
                }

            // 도착 간격이 처리 시간 아래로 내려가지 않는다. burst가 없다는 뜻이다.
            arrivals.zipWithNext().all { (a, b) -> b - a >= slow.inWholeMilliseconds } shouldBe true
            // 만들지 못한 프레임의 timestamp는 건너뛴다
            timestamps.zipWithNext().all { (a, b) -> b - a > 1.seconds / FRAME_RATE } shouldBe true
        }
    }

    "수집을 취소하면 프레임 생성이 멈춘다" {
        runTest {
            var received = 0
            val collecting = launch { source(FakeI420Buffers()).frames().collect { received++ } }
            advanceTimeBy(1.seconds)

            collecting.cancelAndJoin()
            val receivedUntilCancel = received
            advanceTimeBy(1.seconds)

            received shouldBe receivedUntilCancel
        }
    }

    "첫 프레임 전에 취소돼도 버퍼가 남지 않는다" {
        runTest {
            val buffers = FakeI420Buffers()
            var collecting: Job? = null
            val cancelWhileAllocating = object : I420Buffers {
                override fun allocate(width: Int, height: Int): VideoFrame.I420Buffer =
                    buffers.allocate(width, height).also { collecting?.cancel() }
            }
            var received = 0

            collecting = launch { source(cancelWhileAllocating).frames().collect { received++ } }
            collecting.join()

            received shouldBe 0
            buffers.allocated.single().refCount shouldBe 0
        }
    }

    "평면을 채우다 실패해도 버퍼가 남지 않는다" {
        runTest {
            val buffers = FakeI420Buffers()
            val brokenPlanes = object : I420Buffers {
                override fun allocate(width: Int, height: Int): VideoFrame.I420Buffer =
                    BrokenPlaneBuffer(buffers.allocate(width, height))
            }

            shouldThrow<IllegalStateException> {
                source(brokenPlanes).frames().take(1).toList()
            }

            buffers.allocated.single().refCount shouldBe 0
        }
    }

    "수집자가 예외를 던져도 버퍼가 남지 않는다" {
        runTest {
            val buffers = FakeI420Buffers()

            shouldThrow<IllegalStateException> {
                source(buffers).frames().collect { error("소비자 실패") }
            }

            buffers.allocated.single().refCount shouldBe 0
        }
    }

    "보관하지 않으면 흐름이 끝날 때 버퍼가 풀린다" {
        runTest {
            val buffers = FakeI420Buffers()

            source(buffers).frames().take(3).toList()

            buffers.allocated.single().refCount shouldBe 0
        }
    }

    "retain해 보관한 프레임은 release할 때까지 살아 있다" {
        runTest {
            val buffers = FakeI420Buffers()
            val kept = mutableListOf<VideoFrame>()

            source(buffers).frames().take(3).collect { frame ->
                frame.retain()
                kept += frame
            }

            // 소스 몫은 흐름이 끝나며 풀렸고 보관한 셋만 남는다
            buffers.allocated.single().refCount shouldBe 3
            kept.forEach { it.release() }
            buffers.allocated.single().refCount shouldBe 0
        }
    }
})

private class FakeI420Buffers : I420Buffers {
    val allocated = mutableListOf<FakeI420Buffer>()

    override fun allocate(width: Int, height: Int): FakeI420Buffer =
        FakeI420Buffer(width, height).also { allocated += it }
}

/** 평면을 건드리는 순간 실패해 fill 도중 예외를 재현한다. */
private class BrokenPlaneBuffer(private val delegate: FakeI420Buffer) :
    VideoFrame.I420Buffer by delegate {
    override fun getDataY(): ByteBuffer = error("평면 접근 실패")
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
