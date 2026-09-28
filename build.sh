#!/usr/bin/env bash
# Rebuild NEON-CallerID-vX.apk
#
# The sandbox can be reset between messages, so this script re-installs whatever
# is missing (JDK 21 / Gradle / Android SDK) into /opt and then builds.
#
#   bash /home/user/NeonCallerID/build.sh
#
set -e

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk
export GRADLE_USER_HOME=/opt/gradle-home
GRADLE_BIN=/opt/gradle-8.11.1/bin/gradle
PROJECT=/home/user/GodxDownloader
OUT=/home/user/output

echo "sdk.dir=$ANDROID_HOME" > "$PROJECT/local.properties"

# ---- JDK 21 -----------------------------------------------------------------
if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo ">> installing JDK 21"
  sudo apt-get update -qq || true
  sudo apt-get install -y -qq openjdk-21-jdk-headless
fi

# ---- Gradle -----------------------------------------------------------------
if [ ! -x "$GRADLE_BIN" ]; then
  echo ">> installing Gradle 8.11.1"
  sudo mkdir -p /opt && sudo chown -R "$(id -u):$(id -g)" /opt
  [ -f /tmp/gradle.zip ] || curl -sL -o /tmp/gradle.zip \
      https://services.gradle.org/distributions/gradle-8.11.1-bin.zip
  unzip -q -o /tmp/gradle.zip -d /opt
  chmod +x "$GRADLE_BIN"
fi

# ---- Android SDK ------------------------------------------------------------
if [ ! -f "$ANDROID_HOME/platforms/android-35/android.jar" ] \
   || [ ! -f "$ANDROID_HOME/build-tools/34.0.0/aapt2" ]; then
  echo ">> installing Android platform 35 + build-tools 34"
  sudo mkdir -p "$ANDROID_HOME" && sudo chown -R "$(id -u):$(id -g)" "$ANDROID_HOME"
  if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
    [ -f /tmp/cmdtools.zip ] || curl -s -o /tmp/cmdtools.zip \
        https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    unzip -q -o /tmp/cmdtools.zip -d "$ANDROID_HOME/cmdline-tools"
    rm -rf "$ANDROID_HOME/cmdline-tools/latest"
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  fi
  yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses >/dev/null
  yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" \
      "platform-tools" "platforms;android-35" "build-tools;34.0.0" >/dev/null
fi

# ---- build ------------------------------------------------------------------
cd "$PROJECT"
chmod +x gradlew || true
"$GRADLE_BIN" :app:assembleRelease --no-daemon --console=plain

VERSION=$(grep -oP "versionName \"\K[^\"]+" app/build.gradle)
mkdir -p "$OUT"
# Keep only the current APK — old builds blow the ~128 MB snapshot cap.
rm -f "$OUT"/*.apk
cp app/build/outputs/apk/release/app-release.apk "$OUT/GodxShadow-Downloader-v$VERSION.apk"
"$ANDROID_HOME/build-tools/34.0.0/apksigner" verify --print-certs \
    "$OUT/GodxShadow-Downloader-v$VERSION.apk" | head -2
echo ">> $OUT/GodxShadow-Downloader-v$VERSION.apk"
ls -la "$OUT/GodxShadow-Downloader-v$VERSION.apk"
