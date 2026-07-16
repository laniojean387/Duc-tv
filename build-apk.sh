#!/bin/bash
set -e

echo "╔════════════════════════════════════════╗"
echo "║   Building Duc TV APK Installation    ║"
echo "╚════════════════════════════════════════╝"

# Step 1: Create .env if missing
if [ ! -f ".env" ]; then
    echo "✓ Creating .env file..."
    cp .env.example .env
    echo "  GEMINI_API_KEY added from example"
fi

# Step 2: Clean previous builds
echo "✓ Cleaning previous builds..."
./gradlew clean

# Step 3: Build Debug APK
echo "✓ Building Debug APK..."
./gradlew assembleDebug

# Step 4: Check output
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo ""
    echo "╔════════════════════════════════════════╗"
    echo "║  ✓ BUILD SUCCESSFUL!                  ║"
    echo "╚════════════════════════════════════════╝"
    echo ""
    echo "APK Location:"
    echo "  📦 app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    APK_SIZE=$(du -h "app/build/outputs/apk/debug/app-debug.apk" | cut -f1)
    echo "APK Size: $APK_SIZE"
    echo ""
    echo "Installation:"
    echo "  adb install -r app/build/outputs/apk/debug/app-debug.apk"
    echo ""
else
    echo "❌ Build failed!"
    exit 1
fi
