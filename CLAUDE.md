# PingPong — Android 2D Game

## Stack
- Language: Kotlin
- Min SDK: API 26
- Target SDK: API 34
- Rendering: Android Canvas (no game engine)
- Architecture: Simple Activity + Custom View

## Build Commands
- Build: .\gradlew.bat assembleDebug
- Install: adb install app\build\outputs\apk\debug\app-debug.apk
- Build + Install: .\gradlew.bat assembleDebug && adb install app\build\outputs\apk\debug\app-debug.apk

## Game Requirements
- 2 paddles (top = CPU, bottom = Player)
- 1 ball with physics (bounce off walls + paddles)
- Score tracker
- Touch input to move player paddle
- Game loop via Thread + Canvas

## Rules
- Single file game logic in GameView.kt
- No external libraries
- Must build clean with zero warnings
- After every change run gradlew assembleDebug to verify
