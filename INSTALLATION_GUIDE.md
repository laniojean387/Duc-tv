# Guide d'Installation - Duc TV APK

## Prérequis
- Java JDK 11+
- Android SDK (min API 24)
- Gradle 8.0+
- Un appareil Android ou émulateur

## Méthode 1: Build via Script (Automatique)

```bash
chmod +x build-apk.sh
./build-apk.sh
```

L'APK sera généré dans: `app/build/outputs/apk/debug/app-debug.apk`

## Méthode 2: Build Manuel via Gradle

```bash
# Build Debug APK
./gradlew assembleDebug

# Build Release APK (signé)
./gradlew assembleRelease
```

## Méthode 3: Android Studio (GUI)

1. Ouvrir le projet dans Android Studio
2. Build → Build Bundle(s) / APK(s) → Build APK(s)
3. Attendre la compilation

## Installation sur Appareil

### Via ADB (Android Debug Bridge)
```bash
# Installer l'APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Démarrer l'app
adb shell am start -n com.aistudio.ductv.abcxyz/.MainActivity
```

### Via Terminal (Installation directe)
```bash
# Depuis le répertoire du projet
adb push app/build/outputs/apk/debug/app-debug.apk /sdcard/
adb shell pm install /sdcard/app-debug.apk
```

### Via File Manager
1. Copier l'APK sur l'appareil
2. Ouvrir le file manager
3. Naviguer vers l'APK
4. Appuyer pour installer

## Configuration Requise

### .env File
```
GEMINI_API_KEY=YOUR_ACTUAL_GEMINI_API_KEY
```

Obtenez votre clé: https://ai.google.dev/

## Troubleshooting

### Erreur: "compileSdk 36 not installed"
```bash
./gradlew installSdk
```

### Erreur: "GEMINI_API_KEY not found"
```bash
cp .env.example .env
# Éditer .env avec votre vraie clé
```

### Erreur: "No compatible device found"
```bash
adb devices  # Vérifier les appareils connectés
# Ou démarrer un émulateur via Android Studio
```

## Fichiers de Sortie

- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK**: `app/build/outputs/apk/release/app-release.apk`
- **Bundle**: `app/build/outputs/bundle/release/app-release.aab`

## Commandes Utiles

```bash
# Voir la liste des appareils
adb devices

# Uninstall app
adb uninstall com.aistudio.ductv.abcxyz

# View logs
adb logcat

# Nettoyer et rebuild
./gradlew clean assembleDebug
```

## Support
- Documentation: https://developer.android.com/docs
- Gemini API: https://ai.google.dev/
