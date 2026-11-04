SDK ?= $(if $(ANDROID_HOME),$(ANDROID_HOME),$(HOME)/Library/Android/sdk)
BT := $(SDK)/build-tools/34.0.0
ANDROID_JAR := $(SDK)/platforms/android-19/android.jar
OUT := out/hmg-seogaemo.apk
SOURCES := $(shell find app/src modules -name '*.java' -not -path '*/test/*' | sort)
INPUTS := $(shell find app/res modules -type f | sort)

.PHONY: all clean generate module-build map-build test package verify-dist
all: $(OUT)

module-build:
	@set -eu; for hook in modules/*/build/build.sh; do test ! -f "$$hook" || sh "$$hook"; done

map-build:
	mkdir -p out/map-classes out/map-dex out/package/core/map
	javac -source 1.7 -target 1.7 -bootclasspath $(ANDROID_JAR) -cp $(ANDROID_JAR) -d out/map-classes $$(find core/map/src -name '*.java' | sort)
	$(BT)/d8 --min-api 19 --lib $(ANDROID_JAR) --release --output out/map-dex $$(find out/map-classes -name '*.class' | sort)
	touch -t 198001010000 out/map-dex/classes.dex
	rm -f out/package/core/map/hmg-seogaemo-mapper.jar
	cd out/map-dex && zip -q -X ../package/core/map/hmg-seogaemo-mapper.jar classes.dex

generate:
	sh tools/gen-modules.sh

$(OUT): VERSION app/AndroidManifest.xml app/allow-sha1.security $(SOURCES) $(INPUTS) Makefile tools/gen-modules.sh tools/version.py generate module-build
	rm -rf out/classes out/dex
	mkdir -p out/gen out/classes out/dex
	python3 tools/version.py --manifest out/AndroidManifest.xml
	$(BT)/aapt package -f -M out/AndroidManifest.xml -S app/res $(foreach res,$(sort $(wildcard modules/*/res)),-S $(res)) --auto-add-overlay -I $(ANDROID_JAR) -F out/resources.ap_ -J out/gen --custom-package com.seogaemo.hmg
	javac -source 1.7 -target 1.7 -bootclasspath $(ANDROID_JAR) -cp $(ANDROID_JAR) -d out/classes $$(find app/src modules out/gen -name '*.java' -not -path '*/test/*' | sort)
	$(BT)/d8 --min-api 19 --lib $(ANDROID_JAR) --release --output out/dex $$(find out/classes -name '*.class' | sort -u)
	cp out/resources.ap_ out/unsigned.apk
	cd out/dex && zip -q -u ../unsigned.apk classes.dex
	@set -eu; \
	if [ -n "$${RELEASE_KEYSTORE:-}$${RELEASE_KEYSTORE_PASSWORD:-}$${RELEASE_KEY_ALIAS:-}" ]; then \
		: "$${RELEASE_KEYSTORE:?}" "$${RELEASE_KEYSTORE_PASSWORD:?}" "$${RELEASE_KEY_ALIAS:?}"; \
		test -f "$$RELEASE_KEYSTORE"; \
		echo 'Signing release APK'; \
		jarsigner -J-Djava.security.properties=$(CURDIR)/app/allow-sha1.security -sigalg SHA1withRSA -digestalg SHA1 -keystore "$$RELEASE_KEYSTORE" -storepass:env RELEASE_KEYSTORE_PASSWORD -keypass:env RELEASE_KEYSTORE_PASSWORD out/unsigned.apk "$$RELEASE_KEY_ALIAS" >/dev/null; \
	else \
		echo 'Signing debug APK with a throwaway key'; \
		test -f out/debug.keystore || keytool -genkeypair -keystore out/debug.keystore -alias hmg-seogaemo -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname 'CN=HMG-Seogaemo,O=HMG-Seogaemo,C=US' >/dev/null 2>&1; \
		jarsigner -J-Djava.security.properties=$(CURDIR)/app/allow-sha1.security -sigalg SHA1withRSA -digestalg SHA1 -keystore out/debug.keystore -storepass android -keypass android out/unsigned.apk hmg-seogaemo >/dev/null; \
	fi
	$(BT)/zipalign -f 4 out/unsigned.apk $(OUT)
	$(BT)/apksigner verify --verbose --min-sdk-version 19 $(OUT)

package: $(OUT) map-build
	python3 tools/package.py build --aapt $(BT)/aapt

verify-dist:
	python3 tools/package.py verify --aapt $(BT)/aapt

test: export PATH := $(CURDIR)/tools/testlib/fakebin:$(PATH)
test: package
	python3 tools/validate-maps.py all
	python3 tools/test-validate-maps.py
	python3 tools/test-map-evidence.py
	python3 tools/test-map-scanner.py
	python3 tools/test-map-verification.py
	python3 tools/test-map-car.py
	./tools/verify-map maps/hyundai/ioniq5/kor/260902
	python3 tools/test-confirm-routing.py
	sh tools/test-gen-modules.sh
	python3 tools/test-root-shells.py --aapt $(BT)/aapt
	python3 tools/test-android44-shell.py
	python3 tools/lint-android44-shell.py
	python3 tools/lint-module-root.py
	python3 tools/lint-names.py
	mkdir -p out/planner-test
	javac -d out/planner-test app/src/com/seogaemo/hmg/module/ModulePlan.java app/src/com/seogaemo/hmg/module/ModulePlanner.java app/src/com/seogaemo/hmg/module/Warning.java tools/ModulePlannerTest.java
	java -cp out/planner-test ModulePlannerTest
	javac -source 7 -target 7 -d out/planner-test app/src/com/seogaemo/hmg/module/HomeTile.java app/src/com/seogaemo/hmg/module/HomeTileCatalog.java app/src/com/seogaemo/hmg/module/ModuleAction.java tools/HomeTileCatalogTest.java
	java -cp out/planner-test HomeTileCatalogTest
	python3 tools/test-update.py
	mkdir -p out/trust-test
	javac -source 7 -target 7 -d out/trust-test app/src/com/seogaemo/hmg/core/update/UpdateTrust.java tools/UpdateTrustTest.java
	java -cp out/trust-test com.seogaemo.hmg.core.update.UpdateTrustTest app/res/raw/update_roots.pem tools/fixtures/tls/api.github.com.pem tools/fixtures/tls/objects.githubusercontent.com.pem

clean:
	rm -rf out
