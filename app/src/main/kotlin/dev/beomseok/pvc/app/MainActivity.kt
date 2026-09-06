package dev.beomseok.pvc.app

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.beomseok.pvc.capture.withWebRtc
import kotlinx.coroutines.awaitCancellation

private const val TAG = "PvcMain"

/**
 * WebRTC native 라이브러리가 실제로 올라오는지 확인한다.
 * 영상은 다루지 않는다. 생성과 해제가 조용히 끝나는지만 본다.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WebRtcStatus()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "화면 종료. WebRTC 자원 해제가 뒤따른다.")
    }
}

/**
 * 컴포지션에서 벗어나면 [LaunchedEffect]가 취소되고 withWebRtc의 해제가 뒤따른다.
 */
@Composable
private fun WebRtcStatus() {
    var status by remember { mutableStateOf("초기화 중…") }

    LaunchedEffect(Unit) {
        withWebRtc { factory, eglBase ->
            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
            Log.i(TAG, "PeerConnectionFactory 생성됨: $factory")
            Log.i(TAG, "EglBase 생성됨: ${eglBase.eglBaseContext}")
            Log.i(TAG, "ABI=$abi  device=${Build.DEVICE}  sdk=${Build.VERSION.SDK_INT}")

            status = buildString {
                appendLine("WebRTC 초기화 성공")
                appendLine()
                appendLine("ABI  $abi")
                appendLine("SDK  ${Build.VERSION.SDK_INT}")
                append("화면을 닫으면 해제된다.")
            }

            awaitCancellation()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = status, modifier = Modifier.padding(24.dp))
    }
}
