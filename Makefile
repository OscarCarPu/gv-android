APP_ID       := com.gv.app
APP_ID_DEBUG := $(APP_ID).debug
ACTIVITY     := $(APP_ID_DEBUG)/$(APP_ID).MainActivity

# The lights-only build is a separate app (its own applicationId), so both install at once.
LIGHTS_APP_ID       := $(APP_ID).lights
LIGHTS_APP_ID_DEBUG := $(LIGHTS_APP_ID).debug
LIGHTS_ACTIVITY     := $(LIGHTS_APP_ID_DEBUG)/$(APP_ID).MainActivity

APK_DEBUG       := app/build/outputs/apk/full/debug/app-full-debug.apk
APK_RELEASE     := app/build/outputs/apk/full/release/app-full-release.apk
LIGHTS_APK_DEBUG   := app/build/outputs/apk/lights/debug/app-lights-debug.apk
LIGHTS_APK_RELEASE := app/build/outputs/apk/lights/release/app-lights-release.apk

.PHONY: build release install run clean uninstall log devices hooks \
        test lint check test-device \
        build-lights release-lights install-lights run-lights uninstall-lights log-lights

## Configure git to use the tracked hooks in .githooks/
hooks:
	git config core.hooksPath .githooks

# ---------------------------------------------------------------------------
# Full app
# ---------------------------------------------------------------------------

## Build debug APK
build:
	./gradlew assembleFullDebug

## Build release APK and install on connected device.
## Auto-bumps versionCode in version.properties so the APK replaces any prior install.
release:
	@NEW=$$(awk -F= '/^versionCode=/ { print $$2+1; exit }' version.properties) && \
	 sed -i "s/^versionCode=.*/versionCode=$$NEW/" version.properties && \
	 echo "versionCode → $$NEW"
	./gradlew assembleFullRelease
	adb install -r $(APK_RELEASE)

## Build and install debug APK on connected device
install: build
	adb install -r $(APK_DEBUG)

## Build, install, and launch the app
run: install
	adb reverse tcp:8080 tcp:8080
	adb shell am start -n $(ACTIVITY)

## Uninstall debug app from connected device
uninstall:
	adb uninstall $(APP_ID_DEBUG)

## Stream logcat filtered to this app (Ctrl+C to stop)
log:
	adb logcat --pid=$$(adb shell pidof -s $(APP_ID_DEBUG))

# ---------------------------------------------------------------------------
# Lights-only build ("GV Lights") — the Lights tab on its own, as a phone remote.
# Separate applicationId, so it lives beside the full app rather than replacing it.
# ---------------------------------------------------------------------------

## Build the lights-only debug APK
build-lights:
	./gradlew assembleLightsDebug

## Build the lights-only release APK and install it (daily-driver build)
release-lights:
	@NEW=$$(awk -F= '/^versionCode=/ { print $$2+1; exit }' version.properties) && \
	 sed -i "s/^versionCode=.*/versionCode=$$NEW/" version.properties && \
	 echo "versionCode → $$NEW"
	./gradlew assembleLightsRelease
	adb install -r $(LIGHTS_APK_RELEASE)

## Build and install the lights-only debug APK
install-lights: build-lights
	adb install -r $(LIGHTS_APK_DEBUG)

## Build, install, and launch the lights-only app
run-lights: install-lights
	adb reverse tcp:8080 tcp:8080
	adb shell am start -n $(LIGHTS_ACTIVITY)

## Uninstall the lights-only debug app
uninstall-lights:
	adb uninstall $(LIGHTS_APP_ID_DEBUG)

## Stream logcat filtered to the lights-only app
log-lights:
	adb logcat --pid=$$(adb shell pidof -s $(LIGHTS_APP_ID_DEBUG))

# ---------------------------------------------------------------------------
# Shared
# ---------------------------------------------------------------------------

## Clean build artifacts
clean:
	./gradlew clean

## Show connected ADB devices
devices:
	adb devices

## Run JVM unit tests. This is what the pre-commit hook runs, so it must not
## need a device attached.
test:
	./gradlew testFullDebugUnitTest

## Run Android lint over the full flavour
lint:
	./gradlew lintFullDebug

## test + lint
check: test lint

## Run instrumented tests on a connected device
test-device:
	./gradlew connectedFullDebugAndroidTest
