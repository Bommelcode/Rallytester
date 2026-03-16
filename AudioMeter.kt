name: Build Rally Tester APK

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]
  # Also allows manual trigger via the Actions tab
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      # 1. Check out the code
      - name: Checkout code
        uses: actions/checkout@v4

      # 2. Set up JDK 17
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      # 3. Set up Android SDK
      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      # 4. Grant execute permission for gradlew
      - name: Make gradlew executable
        run: chmod +x ./gradlew

      # 5. Cache Gradle dependencies (speeds up future builds)
      - name: Cache Gradle packages
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
          restore-keys: |
            ${{ runner.os }}-gradle-

      # 6. Build debug APK
      - name: Build debug APK
        run: ./gradlew assembleDebug --stacktrace

      # 7. Upload APK as downloadable artifact
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: RallyTester-debug
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 30
