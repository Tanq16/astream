package com.astream

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaCodec: MediaCodec? = null
    private var webSocket: WebSocket? = null
    private var isRunning = false
    private val width = 1920
    private val height = 1080
    private val bitrate = 2000000 // 2Mbps
    private val frameRate = 30

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSharing()
            return START_NOT_STICKY
        }
        val resultData: Intent = intent?.getParcelableExtra("RESULT_DATA") ?: return START_NOT_STICKY
        val url: String = intent.getStringExtra("URL") ?: return START_NOT_STICKY
        startForegroundNotification()
        startProjection(resultData, url)
        return START_STICKY
    }

    private fun startProjection(resultData: Intent, url: String) {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d("Stream", "WebSocket Connected")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("Stream", "WebSocket Closed")
                stopSharing()
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e("Stream", "WebSocket Failure: ${t.message}")
                stopSharing()
            }
        })

        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(android.app.Activity.RESULT_OK, resultData)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                stopSharing()
            }
        }, null)

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
        format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel3)
        format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) 
        format.setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, 30f)
        format.setInteger(MediaFormat.KEY_LATENCY, 0)
        format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000)
        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        val surface = mediaCodec?.createInputSurface()
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, 320,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface, null, null
        )
        mediaCodec?.start()
        isRunning = true
        
        Thread {
            val bufferInfo = MediaCodec.BufferInfo()
            while (isRunning) {
                val codec = mediaCodec ?: break
                try {
                    val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 10000)
                    if (outputBufferId >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferId)
                        val outData = ByteArray(bufferInfo.size)
                        outputBuffer?.get(outData)
                        webSocket?.send(outData.toByteString(0, outData.size))
                        codec.releaseOutputBuffer(outputBufferId, false)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    break
                }
            }
        }.start()
    }

    private fun stopSharing() {
        isRunning = false
        try {
            webSocket?.close(1000, "User Stopped")
            mediaCodec?.stop()
            mediaCodec?.release()
            virtualDisplay?.release()
            mediaProjection?.stop()
        } catch (e: Exception) { e.printStackTrace() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundNotification() {
        val channelId = "screen_share_channel"
        val channel = NotificationChannel(channelId, "Screen Share", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Screen Sharing Active")
            .setContentText("Your screen is being streamed to the server.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        try {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } catch (e: Exception) {
            startForeground(1, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
