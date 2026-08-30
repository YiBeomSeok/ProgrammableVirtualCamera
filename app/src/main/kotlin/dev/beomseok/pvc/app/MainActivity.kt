package dev.beomseok.pvc.app

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.beomseok.pvc.capture.withWebRtc
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

private const val TAG = "PvcMain"

/**
 * WebRTC native 라이브러리가 실제로 올라오는지 확인한다.
 * 영상은 다루지 않는다. 생성과 해제가 조용히 끝나는지만 본다.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val status = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 16f
            setPadding(48, 48, 48, 48)
            text = "초기화 중…"
        }
        setContentView(status)

        // 화면이 사라지면 취소가 전파되어 withWebRtc의 해제가 뒤따른다.
        lifecycleScope.launch {
            withWebRtc { factory, eglBase ->
                val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
                Log.i(TAG, "PeerConnectionFactory 생성됨: $factory")
                Log.i(TAG, "EglBase 생성됨: ${eglBase.eglBaseContext}")
                Log.i(TAG, "ABI=$abi  device=${Build.DEVICE}  sdk=${Build.VERSION.SDK_INT}")

                status.text = buildString {
                    appendLine("WebRTC 초기화 성공")
                    appendLine()
                    appendLine("ABI  $abi")
                    appendLine("SDK  ${Build.VERSION.SDK_INT}")
                    append("화면을 닫으면 해제된다.")
                }

                awaitCancellation()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "화면 종료. WebRTC 자원 해제가 뒤따른다.")
    }
}
