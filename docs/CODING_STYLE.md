# 코딩 스타일

이 프로젝트의 Kotlin/C++ 코드는 아래 기준을 따른다. 커밋과 PR 작성은 `docs/COMMIT_AND_PR_STYLE.local.md`를 따른다.

## 기본 원칙

**검사해서 막는 게 아니라, 애초에 불가능하게 만든다.**

아래 세 항목은 전부 이 원칙의 다른 얼굴이다. 런타임에 조건을 확인하는 코드를, 그런 상태가 존재할 수 없는 구조로 바꾼다.

## 1. 구조로 해결한다

가드절과 try-catch로 문제를 국소적으로 덮지 않는다. 조건 검사로 막으면 그 조건이 코드 전역에 퍼지고, 한 군데만 빠뜨려도 깨진다.

특히 `close()`, `release()` 같은 리소스 정리를 "이러면 닫고 저러면 닫고" 식 if 분기로 흩뿌리지 않는다. 정리는 한 구역에서 처리한다.

### 획득한 블록에서 해제한다

```kotlin
// 나쁨: 만드는 곳과 닫는 곳이 떨어져 있고, 닫을지 말지를 상위가 판단한다
private var codec: MediaCodec? = null

fun start() { codec = MediaCodec.createDecoderByType(mime).apply { start() } }
fun stop()  { if (codec != null && isRunning) { codec?.release(); codec = null } }
```

```kotlin
// 좋음: 만든 자리에서 닫는다. 상위는 취소만 하면 된다
fun frames(): Flow<VideoFrame> = callbackFlow {
    val codec = MediaCodec.createDecoderByType(mime).apply { start() }
    awaitClose { codec.release() }
}
```

### 정책은 선언 한 곳에 모은다

```kotlin
// 나쁨: 드롭이 일어나는 지점마다 release를 붙여야 하고, 하나만 빠뜨려도 샌다
if (queue.size >= 1) { val old = queue.poll(); old.release() }
```

```kotlin
// 좋음: 드롭 정책과 해제 정책이 생성 한 줄에 같이 선언된다
Channel<VideoFrame>(
    capacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
    onUndeliveredElement = { it.release() },
)
```

### 수명은 검사가 아니라 구조로 보장한다

```kotlin
// 나쁨: 매 프레임마다 "아직 살아있나"를 묻는다
fun onFrame(frame: VideoFrame) {
    if (!disposed && surface != null) { sink.consume(frame) }
}
```

```kotlin
// 좋음: join 이후에는 프레임이 존재할 수 없다는 게 구조로 보장된다
suspend fun stop() = captureJob?.cancelAndJoin()
```

### 그 외

- 초기화 여부를 nullable + null 체크로 다루지 않는다. 생성자 주입이나 sealed 타입으로 컴파일러가 강제하게 한다.
- 스레드 규칙은 매 호출 검사 대신 디스패처를 고정해서 보장한다. `if (Thread.currentThread() != glThread) throw ...`를 쓰지 않는다.
- 예외는 각 지점의 try/catch가 아니라 경계 한 곳에서 처리한다. `Flow` 끝의 `catch` 연산자.
- 분기를 없앨 수 있는 연산자를 쓴다. "이전 소스가 있으면 닫고"는 `flatMapLatest`로 사라진다.
- `by lazy`는 side effect와 해제 책임이 없는 immutable 값에만 쓴다. MediaCodec, Camera, EGL, WebRTC 객체와 CoroutineScope처럼 수명이 있는 리소스의 생성을 숨기는 데 쓰지 않는다.

## 2. 코루틴을 100% 쓴다

취소 메커니즘의 예외 전파를 스트림 관리의 축으로 삼는다. 전파 경로에 정리를 얹으면 수동 정리 호출과 그 순서를 신경 쓸 일이 없어진다.

- 스트림은 `Flow`로 표현한다. `start()/stop()` + 별도 스레드 조합을 만들지 않는다.
- 콜백 API는 코루틴 세계로 끌어온다.
  - 다회성 콜백 → `callbackFlow` + `awaitClose`
  - 일회성 콜백 → `suspendCancellableCoroutine`
- 소스 교체는 `flatMapLatest`. 이전 스트림 취소가 자동으로 따라온다.
- 배압과 프레임 드롭은 `Channel` 설정으로 표현한다.
- 테스트는 `runTest` 가상 시간을 쓴다. 30fps 페이싱을 검증하려고 실제로 1초를 기다리지 않는다.
- `runBlocking`은 외부 인터페이스가 동기 반환을 강제하는 경계에서만 쓴다. 그것도 프레임 경로가 아니라 생명주기 경로에서만이고, 왜 여기에만 있는지 주석을 남긴다.

## 3. 인터페이스로 경계를 긋되, 위임은 쓰지 않는다

경계마다 인터페이스를 두고 구현체를 주입한다. 테스트에서 가짜 구현으로 갈아끼울 수 있고, 구현체를 통째로 교체해도 상위 계층이 바뀌지 않는다.

**Kotlin 클래스 위임(`: Interface by impl`)은 쓰지 않는다.** 각 메서드를 명시적으로 구현한다. 위임을 쓰면 실제 동작이 어디서 오는지 흐려진다.

분기가 필요하면 if가 아니라 타입을 나눈다.

```kotlin
// 나쁨: create와 set을 겸용 옵저버 하나로 받아 내부에서 분기한다
class SdpCallback(val isCreate: Boolean, val cont: Continuation<...>) : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) { if (isCreate) ... }
    override fun onSetSuccess() { if (!isCreate) ... }
}
```

```kotlin
// 좋음: 어느 메서드가 no-op인지가 타입에 박힌다. 호출부엔 아무것도 안 남는다
private class CreateSdpObserver(...) : SdpObserver { ... }
private class SetSdpObserver(...) : SdpObserver { ... }
```

## 주석

- 한 주석은 4줄을 넘기지 않는다. `/**`와 `*/`도 줄 수에 포함한다.
- 앞으로의 계획을 적지 않는다. "Phase 2에서 JNI로 옮겨간다" 같은 문장은 곧 사실과 달라지고, 고쳐도 아무도 알아채지 못한다.
- 계획 문서의 단계 번호를 코드에서 참조하지 않는다.
- 코드를 읽으면 아는 것을 다시 쓰지 않는다. 왜 이렇게 했는지가 드러나지 않을 때만 적는다.

## 4. 이 프로젝트에 적용

- `FrameSource`는 `fun frames(): Flow<VideoFrame>` 하나로 표현한다. Phase 2에서 구현체가 JNI 뒤로 넘어가도 이 시그니처는 바뀌지 않는다.
- `FrameTransform`은 `Flow<VideoFrame> -> Flow<VideoFrame>` 형태로 둔다. Phase 2의 native 처리 단계가 소스와 싱크를 안 건드리고 끼어들 자리다.
- `MediaCodec`의 상태(Uninitialized/Configured/Executing/Released)를 블록 밖으로 노출하지 않는다. `callbackFlow` 한 블록 안에 가두고, 바깥에서 보이는 것은 `Flow<VideoFrame>` 하나다.
- EGL 스레드 고정은 `SurfaceTextureHelper`의 핸들러를 디스패처로 감싸서 보장한다.
- `VideoFrame`은 참조 카운팅된다. 소유권 이전 규칙을 파이프라인 단계마다 못 박고, 드롭 경로의 해제는 `Channel` 설정 한 곳에 모은다.
- `org.webrtc.VideoCapturer.stopCapture()`는 `throws InterruptedException`이라 블로킹 계약이다. 주 API는 `suspend fun stop()`으로 두고, 인터페이스 구현은 얇은 다리로만 둔다.
