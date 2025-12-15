# Set Java 17 (required by Android Gradle plugin)
JAVA_HOME ?= /usr/lib/jvm/java-17-openjdk
ADB ?= $(HOME)/Android/Sdk/platform-tools/adb
GRADLE = JAVA_HOME=$(JAVA_HOME) ./gradlew

# WiFi debugging settings (set these or pass as args: make connect PHONE_IP=192.168.1.100)
# PHONE_PORT: Check your phone's Wireless debugging screen for the port (NOT the pairing port)
PHONE_IP ?=
PHONE_PORT ?= 5555

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

# Connect to phone via WiFi debugging (Android 11+)
# Usage: make connect PHONE_IP=192.168.1.100
connect:
ifndef PHONE_IP
	$(error PHONE_IP is required. Usage: make connect PHONE_IP=192.168.1.100)
endif
	$(ADB) connect $(PHONE_IP):$(PHONE_PORT)

# Pair with phone for WiFi debugging (one-time setup, Android 11+)
# Usage: make pair PHONE_IP=192.168.1.100 PAIR_PORT=37000 PAIR_CODE=123456
pair:
ifndef PHONE_IP
	$(error PHONE_IP is required. Usage: make pair PHONE_IP=192.168.1.100 PAIR_PORT=37000 PAIR_CODE=123456)
endif
ifndef PAIR_PORT
	$(error PAIR_PORT is required. Check your phone's wireless debugging pairing dialog)
endif
ifndef PAIR_CODE
	$(error PAIR_CODE is required. Check your phone's wireless debugging pairing dialog)
endif
	$(ADB) pair $(PHONE_IP):$(PAIR_PORT) $(PAIR_CODE)

# Disconnect from WiFi device
disconnect:
	$(ADB) disconnect

# Show connected devices
devices:
	$(ADB) devices -l

# Run on WiFi-connected phone (connect first, then build, install, launch)
# Usage: make run-wifi PHONE_IP=192.168.1.100
run-wifi: connect run
