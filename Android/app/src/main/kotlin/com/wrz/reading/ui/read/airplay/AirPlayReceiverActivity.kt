package com.wrz.reading.ui.read.airplay

import android.app.Activity
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.wrz.reading.R

/**
 * AirPlay 镜像接收画面：全屏 SurfaceView 显示由原生 VideoRenderer 解码的 H.264/H.265 画面。
 * 音频投送不会拉起本 Activity；只有镜像会话开始时才由 [AirPlayReceiverManager] 启动。
 */
class AirPlayReceiverActivity : Activity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_airplay_receiver)
        surfaceView = findViewById(R.id.airplay_surface)
        statusView = findViewById(R.id.airplay_status)
        surfaceView.holder.addCallback(this)
        // 画面到来前显示提示，稍后自动淡出，避免遮挡镜像画面
        statusView.postDelayed({ statusView.visibility = View.GONE }, 4000)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        AirPlayReceiverManager.getInstance().setSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        AirPlayReceiverManager.getInstance().setSurface(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        AirPlayReceiverManager.getInstance().clearSurface(holder.surface)
    }

    override fun onDestroy() {
        surfaceView.holder.removeCallback(this)
        AirPlayReceiverManager.getInstance().onActivityDestroyed()
        super.onDestroy()
    }
}
