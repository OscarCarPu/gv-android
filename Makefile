# Set Java 17 (required by Android Gradle plugin)
JAVA_HOME ?= /usr/lib/jvm/java-17-openjdk
ADB ?= $(HOME)/Android/Sdk/platform-tools/adb
GRADLE = JAVA_HOME=$(JAVA_HOME) ./gradlew

# Build debug APK (output: app/build/outputs/apk/debug/app-debug.apk)
build:
	$(GRADLE) assembleDebug

# Install debug APK on connected device/emulator
install:
	$(GRADLE) installDebug

# Build, install, and launch the app on device/emulator
run: install
	$(ADB) shell am start -n com.ocp.gv/.MainActivity

# Delete all build artifacts
clean:
	$(GRADLE) clean

# Show live logs from the running app
logs:
	$(ADB) logcat --pid=$$($(ADB) shell pidof -s com.ocp.gv)
