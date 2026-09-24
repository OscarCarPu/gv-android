APP_ID       := com.gv.app
APP_ID_DEBUG := $(APP_ID).debug
ACTIVITY     := $(APP_ID_DEBUG)/$(APP_ID).MainActivity

# The semiprivate build is a separate app (its own applicationId), so both install at once.
SEMI_APP_ID         := $(APP_ID).semiprivate
SEMI_APP_ID_DEBUG   := $(SEMI_APP_ID).debug
SEMI_ACTIVITY       := $(SEMI_APP_ID_DEBUG)/$(APP_ID).MainActivity

APK_DEBUG       := app/build/outputs/apk/full/debug/app-full-debug.apk
APK_RELEASE     := app/build/outputs/apk/full/release/app-full-release.apk
SEMI_APK_DEBUG     := app/build/outputs/apk/semiprivate/debug/app-semiprivate-debug.apk
SEMI_APK_RELEASE   := app/build/outputs/apk/semiprivate/release/app-semiprivate-release.apk

.PHONY: build release install run clean uninstall log devices hooks \
        test lint check test-device \
        build-semi release-semi install-semi run-semi uninstall-semi log-semi

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
# Semiprivate build ("GV Semiprivate") — Lights and Rutas, signed in with the semiprivate password.
# Separate applicationId, so it lives beside the full app rather than replacing it.
# ---------------------------------------------------------------------------

## Build the semiprivate debug APK
build-semi:
	./gradlew assembleSemiprivateDebug

## Build the semiprivate release APK and install it (daily-driver build)
release-semi:
	@NEW=$$(awk -F= '/^versionCode=/ { print $$2+1; exit }' version.properties) && \
	 sed -i "s/^versionCode=.*/versionCode=$$NEW/" version.properties && \
	 echo "versionCode → $$NEW"
	./gradlew assembleSemiprivateRelease
	adb install -r $(SEMI_APK_RELEASE)

## Build and install the semiprivate debug APK
install-semi: build-semi
	adb install -r $(SEMI_APK_DEBUG)

## Build, install, and launch the semiprivate app
run-semi: install-semi
	adb reverse tcp:8080 tcp:8080
	adb shell am start -n $(SEMI_ACTIVITY)

## Uninstall the semiprivate debug app
uninstall-semi:
	adb uninstall $(SEMI_APP_ID_DEBUG)

## Stream logcat filtered to the semiprivate app
log-semi:
	adb logcat --pid=$$(adb shell pidof -s $(SEMI_APP_ID_DEBUG))

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
