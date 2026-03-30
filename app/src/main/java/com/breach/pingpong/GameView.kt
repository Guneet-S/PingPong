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
    private var gameThread: Thread? = null
    @Volatile private var isRunning = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var screenW = 0
    private var screenH = 0

    private var ballX = 0f
    private var ballY = 0f
    private var ballRadius = 20f
    private var ballSpeedX = 8f
    private var ballSpeedY = -8f

    private var playerX = 0f
    private var playerY = 0f
    private var paddleW = 250f
    private var paddleH = 30f

    private var cpuX = 0f
    private var cpuY = 0f
    private var cpuSpeed = 6f

    private var playerScore = 0
    private var cpuScore = 0

    @Volatile private var touchX = -1f

    init {
        surfaceHolder.addCallback(this)
        isFocusable = true
    }

    private fun initGame(w: Int, h: Int) {
        screenW = w
        screenH = h

        ballRadius = w * 0.025f
        paddleW = w * 0.28f
        paddleH = h * 0.022f
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
    }

    private fun resetBall(goingDown: Boolean) {
        val speed = screenH * 0.008f
        ballX = screenW / 2f
        ballY = screenH / 2f
        ballSpeedX = speed * (if (Math.random() > 0.5) 1f else -1f)
        ballSpeedY = if (goingDown) speed else -speed
    }

    private fun update() {
        if (screenW == 0 || screenH == 0) return

        ballX += ballSpeedX
        ballY += ballSpeedY

        // Touch: move player paddle
        val tx = touchX
        if (tx >= 0f) {
            playerX = (tx - paddleW / 2f).coerceIn(0f, screenW - paddleW)
        }

        // CPU: follow ball
        val cpuCenter = cpuX + paddleW / 2f
        when {
            cpuCenter < ballX - cpuSpeed -> cpuX += cpuSpeed
            cpuCenter > ballX + cpuSpeed -> cpuX -= cpuSpeed
        }
        cpuX = cpuX.coerceIn(0f, screenW - paddleW)

        // Wall bounce
        if (ballX - ballRadius <= 0f) {
            ballX = ballRadius
            ballSpeedX = Math.abs(ballSpeedX)
        } else if (ballX + ballRadius >= screenW) {
            ballX = screenW - ballRadius
            ballSpeedX = -Math.abs(ballSpeedX)
        }

        // Player paddle collision
        if (ballSpeedY > 0
            && ballY + ballRadius >= playerY
            && ballY - ballRadius <= playerY + paddleH
            && ballX + ballRadius >= playerX
            && ballX - ballRadius <= playerX + paddleW
        ) {
            ballY = playerY - ballRadius
            ballSpeedY = -Math.abs(ballSpeedY)
            val halfW = paddleW / 2f
            if (halfW > 0f) {
                val hit = (ballX - (playerX + halfW)) / halfW
                ballSpeedX = hit * screenH * 0.008f
            }
        }

        // CPU paddle collision
        if (ballSpeedY < 0
            && ballY - ballRadius <= cpuY + paddleH
            && ballY + ballRadius >= cpuY
            && ballX + ballRadius >= cpuX
            && ballX - ballRadius <= cpuX + paddleW
        ) {
            ballY = cpuY + paddleH + ballRadius
            ballSpeedY = Math.abs(ballSpeedY)
            val halfW = paddleW / 2f
            if (halfW > 0f) {
                val hit = (ballX - (cpuX + halfW)) / halfW
                ballSpeedX = hit * screenH * 0.008f
            }
        }

        // Scoring
        if (ballY - ballRadius > screenH) {
            cpuScore++
            resetBall(false)
        } else if (ballY + ballRadius < 0f) {
            playerScore++
            resetBall(true)
        }
    }

    private fun draw() {
        val canvas: Canvas = try {
            surfaceHolder.lockCanvas() ?: return
        } catch (e: Exception) {
            return
        }
        try {
            canvas.drawColor(Color.parseColor("#0D0D1A"))

            // Dashed center line
            paint.color = Color.parseColor("#333355")
            paint.strokeWidth = 4f
            var x = 0f
            while (x < screenW) {
                canvas.drawLine(x, screenH / 2f, x + 24f, screenH / 2f, paint)
                x += 48f
            }

            // CPU paddle (red)
            paint.color = Color.parseColor("#FF4757")
            canvas.drawRoundRect(
                RectF(cpuX, cpuY, cpuX + paddleW, cpuY + paddleH), 14f, 14f, paint
            )

            // Player paddle (blue)
            paint.color = Color.parseColor("#1E90FF")
            canvas.drawRoundRect(
                RectF(playerX, playerY, playerX + paddleW, playerY + paddleH), 14f, 14f, paint
            )

            // Ball
            paint.color = Color.WHITE
            canvas.drawCircle(ballX, ballY, ballRadius, paint)

            // Score
            paint.color = Color.WHITE
            paint.textSize = screenH * 0.07f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("$cpuScore", screenW / 2f, screenH * 0.33f, paint)
            canvas.drawText("$playerScore", screenW / 2f, screenH * 0.72f, paint)
        } finally {
            try {
                surfaceHolder.unlockCanvasAndPost(canvas)
            } catch (e: Exception) {
                // surface was destroyed
            }
        }
    }

    override fun run() {
        val targetMs = 1000L / 60L
        while (isRunning) {
            val start = System.currentTimeMillis()
            update()
            draw()
            val elapsed = System.currentTimeMillis() - start
            val sleep = targetMs - elapsed
            if (sleep > 0) {
                try {
                    Thread.sleep(sleep)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // dimensions may not be set yet; surfaceChanged fires right after with real values
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width > 0 && height > 0) {
            if (screenW == 0) {
                initGame(width, height)
            } else {
                screenW = width
                screenH = height
            }
            if (!isRunning) {
                isRunning = true
                gameThread = Thread(this).also { it.start() }
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRunning = false
        var retry = true
        while (retry) {
            try {
                gameThread?.join()
                retry = false
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                retry = false
            }
        }
        gameThread = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> touchX = event.x
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchX = -1f
        }
        return true
    }

    fun pause() {
        isRunning = false
        try { gameThread?.join() } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        gameThread = null
    }

    fun resume() {
        // Thread is managed by surfaceChanged/surfaceDestroyed
        // Only restart if surface is already available
        if (screenW > 0 && screenH > 0 && !isRunning) {
            isRunning = true
            gameThread = Thread(this).also { it.start() }
        }
    }
}
