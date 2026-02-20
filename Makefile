APP_ID   := com.gv.app
ACTIVITY := $(APP_ID)/.MainActivity
APK_DEBUG := app/build/outputs/apk/debug/app-debug.apk

.PHONY: build release install run clean uninstall log devices test hooks

## Configure git to use the tracked hooks in .githooks/
hooks:
	git config core.hooksPath .githooks

## Build debug APK
build:
	./gradlew assembleDebug

## Build release APK
release:
	./gradlew assembleRelease

## Build and install debug APK on connected device
install: build
	adb install -r $(APK_DEBUG)

## Build, install, and launch the app
run: install
	adb shell am start -n $(ACTIVITY)

## Uninstall the app from connected device
uninstall:
	adb uninstall $(APP_ID)

## Clean build artifacts
clean:
	./gradlew clean

## Show connected ADB devices
devices:
	adb devices

## Run instrumented tests on connected device
test:
	./gradlew connectedDebugAndroidTest

## Stream logcat filtered to this app (Ctrl+C to stop)
log:
	adb logcat --pid=$$(adb shell pidof -s $(APP_ID))
