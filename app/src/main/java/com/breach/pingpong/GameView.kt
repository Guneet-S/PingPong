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
    private var isRunning = false

    // Paint
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Screen dimensions
    private var screenW = 0
    private var screenH = 0

    // Ball
    private var ballX = 0f
    private var ballY = 0f
    private var ballRadius = 0f
    private var ballSpeedX = 0f
    private var ballSpeedY = 0f

    // Player paddle (bottom)
    private var playerX = 0f
    private var playerY = 0f
    private val paddleW get() = screenW * 0.25f
    private val paddleH get() = screenH * 0.02f

    // CPU paddle (top)
    private var cpuX = 0f
    private var cpuY = 0f
    private var cpuSpeed = 0f

    // Scores
    private var playerScore = 0
    private var cpuScore = 0

    // Touch
    private var touchX = -1f

    init {
        surfaceHolder.addCallback(this)
        isFocusable = true
    }

    private fun initGame() {
        ballRadius = screenW * 0.02f
        val baseSpeed = screenH * 0.007f
        cpuSpeed = screenH * 0.004f

        ballX = screenW / 2f
        ballY = screenH / 2f
        ballSpeedX = baseSpeed
        ballSpeedY = -baseSpeed

        playerX = (screenW - paddleW) / 2f
        playerY = screenH - paddleH * 3

        cpuX = (screenW - paddleW) / 2f
        cpuY = paddleH * 2
    }

    private fun update() {
        if (screenW == 0 || screenH == 0) return

        // Move ball
        ballX += ballSpeedX
        ballY += ballSpeedY

        // Move player paddle to touch
        if (touchX >= 0) {
            val targetX = touchX - paddleW / 2f
            playerX = targetX.coerceIn(0f, screenW - paddleW)
        }

        // CPU follows ball
        val cpuCenter = cpuX + paddleW / 2f
        if (cpuCenter < ballX - cpuSpeed) {
            cpuX += cpuSpeed
        } else if (cpuCenter > ballX + cpuSpeed) {
            cpuX -= cpuSpeed
        }
        cpuX = cpuX.coerceIn(0f, screenW - paddleW)

        // Wall bounce (left/right)
        if (ballX - ballRadius <= 0f) {
            ballX = ballRadius
            ballSpeedX = -ballSpeedX
        } else if (ballX + ballRadius >= screenW) {
            ballX = screenW - ballRadius
            ballSpeedX = -ballSpeedX
        }

        // Player paddle collision (bottom)
        val playerRect = RectF(playerX, playerY, playerX + paddleW, playerY + paddleH)
        if (ballSpeedY > 0 &&
            ballY + ballRadius >= playerRect.top &&
            ballY - ballRadius <= playerRect.bottom &&
            ballX + ballRadius >= playerRect.left &&
            ballX - ballRadius <= playerRect.right
        ) {
            ballY = playerRect.top - ballRadius
            ballSpeedY = -ballSpeedY
            // Angle based on hit position
            val hitPos = (ballX - (playerX + paddleW / 2f)) / (paddleW / 2f)
            ballSpeedX = hitPos * screenH * 0.007f
        }

        // CPU paddle collision (top)
        val cpuRect = RectF(cpuX, cpuY, cpuX + paddleW, cpuY + paddleH)
        if (ballSpeedY < 0 &&
            ballY - ballRadius <= cpuRect.bottom &&
            ballY + ballRadius >= cpuRect.top &&
            ballX + ballRadius >= cpuRect.left &&
            ballX - ballRadius <= cpuRect.right
        ) {
            ballY = cpuRect.bottom + ballRadius
            ballSpeedY = -ballSpeedY
            val hitPos = (ballX - (cpuX + paddleW / 2f)) / (paddleW / 2f)
            ballSpeedX = hitPos * screenH * 0.007f
        }

        // Score: ball goes past bottom (CPU scores)
        if (ballY - ballRadius > screenH) {
            cpuScore++
            resetBall(goingDown = false)
        }

        // Score: ball goes past top (Player scores)
        if (ballY + ballRadius < 0) {
            playerScore++
            resetBall(goingDown = true)
        }
    }

    private fun resetBall(goingDown: Boolean) {
        val baseSpeed = screenH * 0.007f
        ballX = screenW / 2f
        ballY = screenH / 2f
        ballSpeedX = baseSpeed * (if (Math.random() > 0.5) 1f else -1f)
        ballSpeedY = if (goingDown) baseSpeed else -baseSpeed
    }

    private fun draw() {
        val canvas: Canvas = surfaceHolder.lockCanvas() ?: return
        try {
            // Background
            canvas.drawColor(Color.parseColor("#1A1A2E"))

            // Center line (dashed effect via segments)
            paint.color = Color.parseColor("#444466")
            paint.strokeWidth = 3f
            var x = 0f
            while (x < screenW) {
                canvas.drawLine(x, screenH / 2f, x + 20f, screenH / 2f, paint)
                x += 40f
            }

            // CPU paddle
            paint.color = Color.parseColor("#E94560")
            canvas.drawRoundRect(cpuX, cpuY, cpuX + paddleW, cpuY + paddleH, 12f, 12f, paint)

            // Player paddle
            paint.color = Color.parseColor("#0F3460")
            canvas.drawRoundRect(playerX, playerY, playerX + paddleW, playerY + paddleH, 12f, 12f, paint)

            // Ball
            paint.color = Color.WHITE
            canvas.drawCircle(ballX, ballY, ballRadius, paint)

            // Score
            paint.color = Color.WHITE
            paint.textSize = screenH * 0.06f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("$cpuScore", screenW / 2f, screenH * 0.35f, paint)
            canvas.drawText("$playerScore", screenW / 2f, screenH * 0.7f, paint)

        } finally {
            surfaceHolder.unlockCanvasAndPost(canvas)
        }
    }

    override fun run() {
        val targetFps = 60L
        val frameTime = 1000L / targetFps

        while (isRunning) {
            val startTime = System.currentTimeMillis()
            update()
            draw()
            val elapsed = System.currentTimeMillis() - startTime
            val sleepTime = frameTime - elapsed
            if (sleepTime > 0) {
                Thread.sleep(sleepTime)
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenW = width
        screenH = height
        if (screenW > 0 && screenH > 0) {
            initGame()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenW = width
        screenH = height
        if (playerScore == 0 && cpuScore == 0) {
            initGame()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        pause()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> touchX = event.x
            MotionEvent.ACTION_UP -> touchX = -1f
        }
        return true
    }

    fun pause() {
        isRunning = false
        gameThread?.join()
        gameThread = null
    }

    fun resume() {
        isRunning = true
        gameThread = Thread(this)
        gameThread?.start()
    }
}
