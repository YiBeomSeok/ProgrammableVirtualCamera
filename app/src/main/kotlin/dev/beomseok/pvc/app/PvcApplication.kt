package dev.beomseok.pvc.app

import android.app.Application
import dev.beomseok.pvc.capture.initializeWebRtc

class PvcApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 프로세스당 한 번. 플랫폼이 보장하므로 별도 확인이 필요 없다.
        initializeWebRtc(this)
    }
}
