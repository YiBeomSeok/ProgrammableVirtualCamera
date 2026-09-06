package dev.beomseok.pvc.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dev.beomseok.pvc.capture.SolidColorFrameSource
import dev.beomseok.pvc.capture.YuvColor
import dev.beomseok.pvc.capture.withEglBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.webrtc.SurfaceViewRenderer

private const val TAG = "PvcMain"
private const val FRAME_WIDTH = 1280
private const val FRAME_HEIGHT = 720
private const val FRAME_RATE = 30

// BT.601 스튜디오 레인지로 옮긴 주황. 아무것도 그리지 않은 화면과 구분된다.
private val PREVIEW_COLOR = YuvColor(y = 146u, u = 53u, v = 193u)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SolidColorPreview()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "화면 종료. 수집 취소와 렌더러, EGL 해제가 뒤따른다.")
    }
}

/**
 * 컴포지션을 벗어나면 수집이 취소되고 렌더러와 EGL 해제가 그 자리에서 일어난다.
 * onFrame은 렌더 스레드의 swapBuffers 락을 기다리므로 메인 스레드에서 부르지 않는다.
 */
@Composable
private fun SolidColorPreview() {
    val context = LocalContext.current
    val renderer = remember { SurfaceViewRenderer(context) }

    LaunchedEffect(renderer) {
        withEglBase { eglBase ->
            renderer.init(eglBase.eglBaseContext, null)
            try {
                withContext(Dispatchers.Default) {
                    SolidColorFrameSource(FRAME_WIDTH, FRAME_HEIGHT, FRAME_RATE, PREVIEW_COLOR)
                        .frames()
                        .collect { frame ->
                            renderer.onFrame(frame)
                            frame.release()
                        }
                }
            } finally {
                renderer.release()
            }
        }
    }

    AndroidView(factory = { renderer }, modifier = Modifier.fillMaxSize())
}
