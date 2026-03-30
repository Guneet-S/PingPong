package com.breach.pingpong

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private val surfaceHolder: SurfaceHolder = holder

    @Volatile private var isRunning = false
    private var gameThread: Thread? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    @Volatile private var screenW = 0
    @Volatile private var screenH = 0

    private var ballX = 0f
    private var ballY = 0f
    private var ballRadius = 20f
    private var ballSpeedX = 0f
    private var ballSpeedY = 0f

    private var playerX = 0f
    private var playerY = 0f
    private var paddleW = 0f
    private var paddleH = 0f

    private var cpuX = 0f
    private var cpuY = 0f
    private var cpuSpeed = 0f

    private var playerScore = 0
    private var cpuScore = 0

    @Volatile private var touchX = -1f

    private var initialized = false

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    private fun initGame(w: Int, h: Int) {
        screenW = w
        screenH = h
        paddleW = w * 0.28f
        paddleH = h * 0.022f
        ballRadius = w * 0.025f
        cpuSpeed = h * 0.005f

        val speed = h * 0.008f
        ballX = w / 2f
        ballY = h / 2f
        ballSpeedX = speed
        ballSpeedY = -speed

        playerX = (w - paddleW) / 2f
        playerY = h - paddleH * 4f
        cpuX = (w - paddleW) / 2f
        cpuY = paddleH * 3f
        initialized = true
    }

    private fun resetBall(goingDown: Boolean) {
        val speed = screenH * 0.008f
        ballX = screenW / 2f
        ballY = screenH / 2f
        ballSpeedX = speed * if (Math.random() > 0.5) 1f else -1f
        ballSpeedY = if (goingDown) speed else -speed
    }

    private fun update() {
        if (!initialized) return

        ballX += ballSpeedX
        ballY += ballSpeedY

        val tx = touchX
        if (tx >= 0f) {
            playerX = (tx - paddleW / 2f).coerceIn(0f, screenW - paddleW)
        }

        val cpuCenter = cpuX + paddleW / 2f
        when {
            cpuCenter < ballX - cpuSpeed -> cpuX += cpuSpeed
            cpuCenter > ballX + cpuSpeed -> cpuX -= cpuSpeed
        }
        cpuX = cpuX.coerceIn(0f, screenW - paddleW)

        if (ballX - ballRadius <= 0f) {
            ballX = ballRadius
            ballSpeedX = Math.abs(ballSpeedX)
        } else if (ballX + ballRadius >= screenW) {
            ballX = screenW - ballRadius
            ballSpeedX = -Math.abs(ballSpeedX)
        }

        // Player paddle hit
        if (ballSpeedY > 0
            && ballY + ballRadius >= playerY
            && ballY - ballRadius <= playerY + paddleH
            && ballX >= playerX - ballRadius
            && ballX <= playerX + paddleW + ballRadius
        ) {
            ballY = playerY - ballRadius
            ballSpeedY = -Math.abs(ballSpeedY)
            if (paddleW > 0f) {
                val hit = (ballX - (playerX + paddleW / 2f)) / (paddleW / 2f)
                ballSpeedX = hit.coerceIn(-1.5f, 1.5f) * screenH * 0.008f
            }
        }

        // CPU paddle hit
        if (ballSpeedY < 0
            && ballY - ballRadius <= cpuY + paddleH
            && ballY + ballRadius >= cpuY
            && ballX >= cpuX - ballRadius
            && ballX <= cpuX + paddleW + ballRadius
        ) {
            ballY = cpuY + paddleH + ballRadius
            ballSpeedY = Math.abs(ballSpeedY)
            if (paddleW > 0f) {
                val hit = (ballX - (cpuX + paddleW / 2f)) / (paddleW / 2f)
                ballSpeedX = hit.coerceIn(-1.5f, 1.5f) * screenH * 0.008f
            }
        }

        if (ballY - ballRadius > screenH) {
            cpuScore++
            resetBall(false)
        } else if (ballY + ballRadius < 0f) {
            playerScore++
            resetBall(true)
        }
    }

    private fun draw() {
        if (!surfaceHolder.surface.isValid) return
        var canvas: Canvas? = null
        try {
            canvas = surfaceHolder.lockCanvas()
            if (canvas == null) return

            canvas.drawColor(Color.parseColor("#0D0D1A"))

            if (!initialized) return

            // Center dashed line
            paint.color = Color.parseColor("#333355")
            paint.strokeWidth = 4f
            paint.style = Paint.Style.STROKE
            var x = 0f
            while (x < screenW) {
                canvas.drawLine(x, screenH / 2f, x + 24f, screenH / 2f, paint)
                x += 48f
            }

            paint.style = Paint.Style.FILL

            // CPU paddle
            paint.color = Color.parseColor("#FF4757")
            canvas.drawRoundRect(RectF(cpuX, cpuY, cpuX + paddleW, cpuY + paddleH), 14f, 14f, paint)

            // Player paddle
            paint.color = Color.parseColor("#1E90FF")
            canvas.drawRoundRect(RectF(playerX, playerY, playerX + paddleW, playerY + paddleH), 14f, 14f, paint)

            // Ball
            paint.color = Color.WHITE
            canvas.drawCircle(ballX, ballY, ballRadius, paint)

            // Score
            paint.color = Color.WHITE
            paint.textSize = screenH * 0.07f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("$cpuScore", screenW / 2f, screenH * 0.33f, paint)
            canvas.drawText("$playerScore", screenW / 2f, screenH * 0.72f, paint)

        } catch (e: Exception) {
            // surface temporarily unavailable
        } finally {
            if (canvas != null) {
                try {
                    surfaceHolder.unlockCanvasAndPost(canvas)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    override fun run() {
        val targetMs = 1000L / 60L
        while (isRunning) {
            val start = System.currentTimeMillis()
            try {
                update()
                draw()
            } catch (e: Exception) {
                // keep loop alive
            }
            val sleep = targetMs - (System.currentTimeMillis() - start)
            if (sleep > 0) {
                try { Thread.sleep(sleep) } catch (e: InterruptedException) { break }
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) { }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (!initialized) {
            initGame(width, height)
        }
        startThread()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopThread()
    }

    private fun startThread() {
        if (isRunning) return
        isRunning = true
        gameThread = Thread(this, "GameThread").also { it.start() }
    }

    private fun stopThread() {
        isRunning = false
        gameThread?.join(500)
        gameThread = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> touchX = event.x
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchX = -1f
        }
        return true
    }

    fun pause() { stopThread() }

    fun resume() {
        if (initialized && !isRunning) startThread()
    }
}
