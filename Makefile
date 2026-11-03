SDK ?= $(if $(ANDROID_HOME),$(ANDROID_HOME),$(HOME)/Library/Android/sdk)
BT := $(SDK)/build-tools/34.0.0
ANDROID_JAR := $(SDK)/platforms/android-19/android.jar
OUT := out/seogaemo.apk
SOURCES := $(shell find app/src modules -name '*.java' | sort)
INPUTS := $(shell find app/res modules -type f | sort)

.PHONY: all clean generate test package verify-dist
all: $(OUT)

generate:
	sh tools/gen-modules.sh

$(OUT): VERSION app/AndroidManifest.xml app/allow-sha1.security $(SOURCES) $(INPUTS) Makefile tools/gen-modules.sh tools/version.py generate
	rm -rf out/classes out/dex
	mkdir -p out/gen out/classes out/dex
	python3 tools/version.py --manifest out/AndroidManifest.xml
	$(BT)/aapt package -f -M out/AndroidManifest.xml -S app/res $(foreach res,$(sort $(wildcard modules/*/res)),-S $(res)) --auto-add-overlay -I $(ANDROID_JAR) -F out/resources.ap_ -J out/gen --custom-package com.seogaemo.app
	javac -source 1.7 -target 1.7 -bootclasspath $(ANDROID_JAR) -cp $(ANDROID_JAR) -d out/classes $$(find app/src modules out/gen -name '*.java' | sort)
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
		test -f out/debug.keystore || keytool -genkeypair -keystore out/debug.keystore -alias seogaemo -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname 'CN=seogaemo,O=seogaemo,C=US' >/dev/null 2>&1; \
		jarsigner -J-Djava.security.properties=$(CURDIR)/app/allow-sha1.security -sigalg SHA1withRSA -digestalg SHA1 -keystore out/debug.keystore -storepass android -keypass android out/unsigned.apk seogaemo >/dev/null; \
	fi
	$(BT)/zipalign -f 4 out/unsigned.apk $(OUT)
	$(BT)/apksigner verify --verbose --min-sdk-version 19 $(OUT)

package: $(OUT)
	python3 tools/package.py build --aapt $(BT)/aapt

verify-dist:
	python3 tools/package.py verify --aapt $(BT)/aapt

test: package
	sh tools/test-gen-modules.sh
	python3 tools/test-package.py --aapt $(BT)/aapt

clean:
	rm -rf out
