SDK ?= $(if $(ANDROID_HOME),$(ANDROID_HOME),$(HOME)/Library/Android/sdk)
BT := $(SDK)/build-tools/34.0.0
ANDROID_JAR := $(SDK)/platforms/android-19/android.jar
OUT := out/hmg-seogaemo.apk
SOURCES := $(shell find app/src modules -name '*.java' -not -path '*/test/*' | sort)
INPUTS := $(shell find app/res modules -type f | sort)

.PHONY: all clean generate module-build map-build hsm-build hook-build test package verify-dist
all: $(OUT)

# CarPlay compiles against the hook SDK jar that hook-build writes.
module-build: hook-build
	@set -eu; for hook in modules/*/build/build.sh; do test ! -f "$$hook" || sh "$$hook"; done

map-build:
	mkdir -p out/map-classes out/map-dex out/package/core/map
	javac -source 1.7 -target 1.7 -bootclasspath $(ANDROID_JAR) -cp $(ANDROID_JAR) -d out/map-classes $$(find core/map/src -name '*.java' | sort)
	$(BT)/d8 --min-api 19 --lib $(ANDROID_JAR) --release --output out/map-dex $$(find out/map-classes -name '*.class' | sort)
	touch -t 198001010000 out/map-dex/classes.dex
	rm -f out/package/core/map/hmg-seogaemo-mapper.jar
	cd out/map-dex && zip -q -X ../package/core/map/hmg-seogaemo-mapper.jar classes.dex

hsm-build:
	rm -rf out/hsm-classes out/hsm-dex
	mkdir -p out/hsm-classes out/hsm-dex out/package/core/hsm
	javac -source 1.7 -target 1.7 -bootclasspath $(ANDROID_JAR) -cp $(ANDROID_JAR) -d out/hsm-classes $$(find core/hsm/src -name '*.java' | sort)
	$(BT)/d8 --min-api 19 --lib $(ANDROID_JAR) --release --output out/hsm-dex $$(find out/hsm-classes -name '*.class' | sort)
	touch -t 198001010000 out/hsm-dex/classes.dex
	rm -f out/package/core/hsm/hmg-seogaemo-hsm.jar
	cd out/hsm-dex && zip -q -X ../package/core/hsm/hmg-seogaemo-hsm.jar classes.dex

hook-build:
	test ! -f core/hook/build.sh || sh core/hook/build.sh

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

package: $(OUT) map-build hsm-build hook-build
	python3 tools/package.py build --aapt $(BT)/aapt

verify-dist:
	python3 tools/package.py verify --aapt $(BT)/aapt

TEST_JOBS ?= $(shell getconf _NPROCESSORS_ONLN 2>/dev/null || echo 2)
.PHONY: test-suites test-dist test-java

# One pool for every suite (tools/run-tests.py): root/module suites on mksh (plus a
# /bin/sh smoke set), host checks and test-java. TESTS_ONLY=word,... selects tasks.
test:
	$(MAKE) -j$(TEST_JOBS) package
	$(MAKE) test-suites

# The suites alone, on the package already in out/ (never rebuilds or re-signs it).
test-suites: export PATH := $(CURDIR)/tools/testlib/fakebin:$(PATH)
test-suites:
	python3 tools/run-tests.py --aapt $(BT)/aapt --jobs $(TEST_JOBS) $(if $(TESTS_ONLY),--only $(TESTS_ONLY))

# Suites that read the completed package: the release workflow runs these on the
# release-signed build when CI already passed the full suite on the same commit.
test-dist:
	$(MAKE) test-suites TESTS_ONLY=test-package.py,test-entry-dispatch.py

# Each Java test compiles only the app classes it needs, against Java 7 source/target.
JAVAC7 := javac -encoding UTF-8 -source 7 -target 7
APPSRC := app/src/com/seogaemo/hmg
test-java:
	rm -rf out/java-test && mkdir -p out/java-test/planner out/java-test/tile out/java-test/trust out/java-test/guards out/java-test/installed out/java-test/wifi out/java-test/qr
	$(JAVAC7) -d out/java-test/planner $(addprefix $(APPSRC)/module/,ModulePlan.java ModulePlanner.java Warning.java) tools/ModulePlannerTest.java
	java -cp out/java-test/planner ModulePlannerTest
	$(JAVAC7) -d out/java-test/tile $(addprefix $(APPSRC)/module/,HomeTile.java HomeTileCatalog.java ModuleAction.java) tools/HomeTileCatalogTest.java
	java -cp out/java-test/tile HomeTileCatalogTest
	$(JAVAC7) -d out/java-test/trust $(APPSRC)/core/update/UpdateTrust.java tools/UpdateTrustTest.java
	java -cp out/java-test/trust com.seogaemo.hmg.core.update.UpdateTrustTest app/res/raw/update_roots.pem tools/fixtures/tls/api.github.com.pem tools/fixtures/tls/objects.githubusercontent.com.pem
	$(JAVAC7) -d out/java-test/guards $(APPSRC)/core/update/BoundedProcess.java $(addprefix $(APPSRC)/module/,ModuleResume.java RestoreGate.java RootResult.java) tools/AppGuardsTest.java
	java -cp out/java-test/guards AppGuardsTest
	$(JAVAC7) -d out/java-test/installed $(addprefix $(APPSRC)/module/,InstalledModule.java RootResult.java RootClient.java ModuleRegistry.java ParallelReads.java Module.java ModuleAction.java InfoRow.java HomeTile.java Warning.java ModuleStatus.java ModulePlan.java ModulePlanner.java) $(APPSRC)/ui/settings/SettingsPage.java tools/InstalledModuleTest.java
	java -cp out/java-test/installed com.seogaemo.hmg.module.InstalledModuleTest
	$(JAVAC7) -d out/java-test/wifi $(APPSRC)/module/WifiNetwork.java tools/WifiNetworkTest.java
	java -cp out/java-test/wifi com.seogaemo.hmg.module.WifiNetworkTest
	$(JAVAC7) -d out/java-test/qr $(APPSRC)/ui/QrCode.java $(APPSRC)/core/IssueLink.java tools/QrCodeTest.java
	java -cp out/java-test/qr QrCodeTest tools/fixtures/qr

clean:
	rm -rf out
