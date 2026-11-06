#!/bin/sh
# Reproducible build of the Realtek USB Wi-Fi modules (88x2bu, 8851bu) for
# Hyundai/Kia/Genesis Gen5W head units (Telechips tcc803x and tcc897x).
#
#   sh kernel/usb-wifi/build.sh      run on the host; builds the Docker image
#                                    and runs the build inside it
#   JOBS=N                           parallel make jobs (default: all CPUs)
#
# Results land in kernel/usb-wifi/out/<platform>/ with SHA256SUMS.
set -eu

KERNEL_VERSION=3.18.24
KERNEL_URL=https://cdn.kernel.org/pub/linux/kernel/v3.x/linux-$KERNEL_VERSION.tar.xz
KERNEL_SHA256=ed54cf8c3ac6b95fc17b07b3a236bc42719c96fee08fe696cabfb5b8e07f8c1b

# Gitiles regenerates the archive on every request (the gzip/tar bytes change),
# so the toolchain is pinned by commit and by a hash over its extracted files.
TOOLCHAIN_COMMIT=8b880805d454664b3eed11d0f053cdeafa1ff06e
TOOLCHAIN_URL=https://android.googlesource.com/platform/prebuilts/gcc/linux-x86/arm/arm-eabi-4.7/+archive/$TOOLCHAIN_COMMIT.tar.gz
TOOLCHAIN_TREE_SHA256=dac5133ab9b74a0350288bbd18fca0e21826410b385c94e83d9dbbcdf326c975

RTL88X2BU_URL=https://github.com/cilynx/rtl88x2bu.git
RTL88X2BU_COMMIT=04ed7009bac4b235fe1880ba29e23159f49a6121
RTL8851BU_URL=https://github.com/fofajardo/rtl8851bu.git
RTL8851BU_COMMIT=9e37e7a1101316a05d5daede91549ea779b64539

CAR_VERMAGIC='3.18.24-tcc SMP preempt mod_unload ARMv7 p2v8'
PLATFORMS='tcc803x tcc897x'
IMAGE=hmg-seogaemo-usb-wifi
# Kernel trees need a case-sensitive file system (xt_TCPMSS.c next to
# xt_tcpmss.c), which macOS bind mounts are not, so they live in a volume.
WORK_VOLUME=hmg-seogaemo-usb-wifi-work

# Car config symbols left out of the module build tree because only the
# Telechips kernel source provides what they include:
#   NEED_MACH_IO_H  -> <mach/io.h>      WAKELOCK -> <linux/wakelock.h>
# The 3.18.24 Makefile turns CC_STACKPROTECTOR_STRONG into
# -fstack-protector-strong, which GCC 4.7 rejects; modules use the regular
# stack protector instead. None of these changes the vermagic string.
DROPPED_SYMBOLS='CONFIG_NEED_MACH_IO_H CONFIG_WAKELOCK CONFIG_CC_STACKPROTECTOR_STRONG'

die() { echo "build.sh: $*" >&2; exit 1; }
log() { echo "== $*"; }

here=$(cd "$(dirname "$0")" && pwd)

if [ "${HMG_SEOGAEMO_USBWIFI_INSIDE:-}" != 1 ]; then
	command -v docker >/dev/null 2>&1 || die "docker is required"
	docker build --platform linux/amd64 -t "$IMAGE" "$here"
	mkdir -p "$here/out"
	exec docker run --rm --platform linux/amd64 \
		-e HMG_SEOGAEMO_USBWIFI_INSIDE=1 -e JOBS="${JOBS:-}" \
		-v "$here:/usb-wifi:ro" -v "$here/out:/out" -v "$WORK_VOLUME:/work" \
		"$IMAGE" sh /usb-wifi/build.sh
fi

SRC=/usb-wifi
OUT=/out
CACHE=$OUT/cache
WORK=/work
JOBS=${JOBS:-$(nproc)}
mkdir -p "$CACHE" "$WORK"

nr_irqs() {
	case $1 in
	tcc803x) echo 256 ;;
	tcc897x) echo 160 ;;
	*) die "unknown platform $1" ;;
	esac
}

fetch() {
	[ -f "$2" ] && return 0
	curl -fsSL --retry 3 -o "$2.part" "$1"
	mv "$2.part" "$2"
}

tree_sha256() (
	cd "$1"
	find . -type f -o -type l | LC_ALL=C sort | while IFS= read -r f; do
		if [ -L "$f" ]; then
			echo "L $(readlink "$f") $f"
		else
			echo "$(sha256sum "$f" | cut -d' ' -f1) $f"
		fi
	done | sha256sum | cut -d' ' -f1
)

get_kernel() {
	tarball=$CACHE/linux-$KERNEL_VERSION.tar.xz
	fetch "$KERNEL_URL" "$tarball"
	echo "$KERNEL_SHA256  $tarball" | sha256sum -c - >/dev/null \
		|| die "kernel tarball sha256 mismatch"
	if [ ! -d "$WORK/linux-pristine" ]; then
		rm -rf "$WORK/linux-unpack"
		mkdir -p "$WORK/linux-unpack"
		tar -xJf "$tarball" -C "$WORK/linux-unpack"
		mv "$WORK/linux-unpack/linux-$KERNEL_VERSION" "$WORK/linux-pristine"
		rmdir "$WORK/linux-unpack"
	fi
	log "kernel linux-$KERNEL_VERSION sha256 $KERNEL_SHA256 ok"
}

get_toolchain() {
	tc=$WORK/arm-eabi-4.7
	if [ ! -x "$tc/bin/arm-eabi-gcc" ]; then
		archive=$CACHE/arm-eabi-4.7-$TOOLCHAIN_COMMIT.tar.gz
		fetch "$TOOLCHAIN_URL" "$archive"
		rm -rf "$tc"
		mkdir -p "$tc"
		tar -xzf "$archive" -C "$tc"
	fi
	[ "$(tree_sha256 "$tc")" = "$TOOLCHAIN_TREE_SHA256" ] \
		|| die "toolchain tree sha256 mismatch"
	PATH=$tc/bin:$PATH
	export PATH
	log "toolchain arm-eabi-4.7 @$TOOLCHAIN_COMMIT tree sha256 ok: $(arm-eabi-gcc -dumpversion)"
}

get_driver() {
	name=$1 url=$2 commit=$3
	repo=$CACHE/$name.git
	if [ ! -d "$repo" ]; then
		git init -q --bare "$repo"
	fi
	if ! git --git-dir="$repo" cat-file -e "$commit^{commit}" 2>/dev/null; then
		git --git-dir="$repo" fetch -q --depth 1 "$url" "$commit"
	fi
	[ "$(git --git-dir="$repo" rev-parse "$commit^{commit}")" = "$commit" ] \
		|| die "$name: pinned commit $commit not found"
	log "driver $name @$commit"
}

kmake() {
	make ARCH=arm CROSS_COMPILE=arm-eabi- KBUILD_BUILD_USER=hmg-seogaemo \
		KBUILD_BUILD_HOST=build KBUILD_BUILD_TIMESTAMP=pinned -j"$JOBS" "$@"
}

# Write include/config/auto.conf and include/generated/autoconf.h straight from
# the car's .config. Vanilla Kconfig does not know the Telechips symbols, so
# olddefconfig alone would change values that shape structures (LPAE, SMP,
# spinlock debugging, ...). Modules must see the exact car values.
write_car_autoconf() {
	cfg=$1 tree=$2
	conf=$tree/include/config/auto.conf
	hdr=$tree/include/generated/autoconf.h
	echo "# Generated by HMG-Seogaemo build.sh from $(basename "$cfg")" > "$conf"
	echo "/* Generated by HMG-Seogaemo build.sh from $(basename "$cfg") */" > "$hdr"
	while IFS= read -r line; do
		case $line in
		CONFIG_*=*) ;;
		*) continue ;;
		esac
		name=${line%%=*}
		value=${line#*=}
		case " $DROPPED_SYMBOLS " in
		*" $name "*) continue ;;
		esac
		echo "$line" >> "$conf"
		case $value in
		y) echo "#define $name 1" >> "$hdr" ;;
		m) echo "#define ${name}_MODULE 1" >> "$hdr" ;;
		n) ;;
		*) echo "#define $name $value" >> "$hdr" ;;
		esac
	done < "$cfg"
	if grep -q '^CONFIG_CC_STACKPROTECTOR_STRONG=y' "$cfg"; then
		echo 'CONFIG_CC_STACKPROTECTOR_REGULAR=y' >> "$conf"
		echo '#define CONFIG_CC_STACKPROTECTOR_REGULAR 1' >> "$hdr"
	fi
}

prepare_module_tree() {
	plat=$1
	tree=$WORK/kernel-$plat
	cfg=$SRC/configs/$plat.config
	log "[$plat] module build tree"
	rm -rf "$tree"
	cp -a "$WORK/linux-pristine" "$tree"
	cp "$cfg" "$tree/.config"
	kmake -C "$tree" olddefconfig > "$WORK/$plat-olddefconfig.log"
	kmake -C "$tree" prepare scripts > "$WORK/$plat-prepare.log"
	write_car_autoconf "$cfg" "$tree"
	# The car config has no SPARSE_IRQ, so <asm/irq.h> needs the machine's
	# NR_IRQS from <mach/irqs.h>, which vanilla 3.18 lacks for Telechips.
	mkdir -p "$tree/include/mach"
	cat > "$tree/include/mach/irqs.h" <<EOF
/* Generated by HMG-Seogaemo build.sh: Telechips $plat interrupt count. */
#ifndef __HMG_SEOGAEMO_MACH_IRQS_H
#define __HMG_SEOGAEMO_MACH_IRQS_H
#define NR_IRQS $(nr_irqs "$plat")
#endif
EOF
	touch "$tree/include/config/auto.conf"
	rm -f "$tree/include/generated/bounds.h" "$tree/include/generated/asm-offsets.h"
	kmake -C "$tree" prepare > "$WORK/$plat-prepare-car.log"
	release=$(cat "$tree/include/config/kernel.release")
	[ "$release" = "3.18.24-tcc" ] || die "[$plat] kernel release is '$release'"
}

# A full vanilla 3.18.24 vmlinux built from the car config (Telechips machine
# options replaced by the generic ARMv7 virt machine) provides Module.symvers,
# so modpost can report every symbol the modules need but the kernel lacks.
build_exports_tree() {
	plat=$1
	tree=$WORK/exports-$plat
	cfg=$SRC/configs/$plat.config
	if [ -f "$tree/.hmg-seogaemo-done" ]; then
		log "[$plat] export tree cached"
		return 0
	fi
	log "[$plat] export tree: vmlinux + Module.symvers (slow)"
	rm -rf "$tree"
	cp -a "$WORK/linux-pristine" "$tree"
	grep -v -e '^CONFIG_CC_STACKPROTECTOR' "$cfg" > "$tree/.config"
	{
		echo 'CONFIG_ARCH_MULTIPLATFORM=y'
		echo 'CONFIG_ARCH_MULTI_V7=y'
		echo 'CONFIG_ARCH_VIRT=y'
		echo 'CONFIG_CC_STACKPROTECTOR_REGULAR=y'
	} >> "$tree/.config"
	kmake -C "$tree" olddefconfig > "$WORK/$plat-exports-config.log"
	for sym in CONFIG_CPU_V7 CONFIG_SMP CONFIG_PREEMPT CONFIG_MODULE_UNLOAD \
		CONFIG_ARM_PATCH_PHYS_VIRT CONFIG_ARM_LPAE CONFIG_CFG80211 CONFIG_USB; do
		want=$(grep "^$sym=" "$cfg" || true)
		have=$(grep "^$sym=" "$tree/.config" || true)
		[ "$want" = "$have" ] || die "[$plat] export tree $sym: car '$want', tree '$have'"
	done
	kmake -C "$tree" vmlinux modules > "$WORK/$plat-exports-build.log" 2>&1 \
		|| { tail -40 "$WORK/$plat-exports-build.log"; die "[$plat] export tree build failed"; }
	[ -s "$tree/Module.symvers" ] || die "[$plat] no Module.symvers"
	touch "$tree/.hmg-seogaemo-done"
}

build_driver() {
	plat=$1 name=$2 commit=$3 module=$4 kconfig=$5
	tree=$WORK/kernel-$plat
	exports=$WORK/exports-$plat/Module.symvers
	drv=$WORK/build-$plat/$name
	dest=$OUT/$plat
	logf=$WORK/build-$plat/$name.log
	log "[$plat] $module.ko"
	rm -rf "$drv"
	mkdir -p "$drv" "$dest"
	git --git-dir="$CACHE/$name.git" archive "$commit" | tar -x -C "$drv"
	for p in "$SRC"/patches/"$name"-*.patch; do
		[ -f "$p" ] || die "no patches for $name"
		(cd "$drv" && git apply --whitespace=nowarn "$p") || die "$name: $(basename "$p") does not apply"
	done
	cp "$exports" "$tree/Module.symvers"
	kmake -C "$tree" M="$drv" TopDIR="$drv" "$kconfig=m" modules > "$logf" 2>&1 \
		|| { grep -E 'error|Error' "$logf" | head -40; die "[$plat] $module build failed (log: $logf)"; }
	if grep -q 'undefined!' "$logf"; then
		grep 'undefined!' "$logf"
		die "[$plat] $module.ko needs symbols the kernel does not export"
	fi
	arm-eabi-strip --strip-debug -o "$dest/$module.ko" "$drv/$module.ko"
	verify_module "$plat" "$dest/$module.ko" "$exports"
}

verify_module() {
	plat=$1 ko=$2 exports=$3
	arm-eabi-objcopy -O binary --only-section=.modinfo "$ko" "$WORK/modinfo.bin"
	vermagic=$(tr '\0' '\n' < "$WORK/modinfo.bin" | sed -n 's/^vermagic=//p')
	vermagic=${vermagic% }
	[ "$vermagic" = "$CAR_VERMAGIC" ] \
		|| die "[$plat] $(basename "$ko") vermagic '$vermagic' != car '$CAR_VERMAGIC'"
	echo "   vermagic: $vermagic (matches car)"
	cut -f2 "$exports" | LC_ALL=C sort -u > "$WORK/exports.list"
	arm-eabi-nm -u "$ko" | awk '{print $2}' | LC_ALL=C sort -u > "$WORK/needed.list"
	missing=$(LC_ALL=C comm -23 "$WORK/needed.list" "$WORK/exports.list")
	[ -z "$missing" ] || die "[$plat] $(basename "$ko") missing exports: $missing"
	cp "$WORK/needed.list" "${ko%.ko}.undefined-symbols.txt"
	echo "   undefined symbols: $(wc -l < "$WORK/needed.list"), all exported by 3.18.24 with the $plat config"
}

get_kernel
get_toolchain
get_driver rtl88x2bu "$RTL88X2BU_URL" "$RTL88X2BU_COMMIT"
get_driver rtl8851bu "$RTL8851BU_URL" "$RTL8851BU_COMMIT"

for plat in $PLATFORMS; do
	build_exports_tree "$plat"
	prepare_module_tree "$plat"
	build_driver "$plat" rtl88x2bu "$RTL88X2BU_COMMIT" 88x2bu CONFIG_RTL8822BU
	build_driver "$plat" rtl8851bu "$RTL8851BU_COMMIT" 8851bu CONFIG_RTL8851BU
done

log "results"
(cd "$OUT" && sha256sum */*.ko > SHA256SUMS && cat SHA256SUMS)
for plat in $PLATFORMS; do
	ls -l "$OUT/$plat"/*.ko | awk '{print "   " $5 " bytes  " $NF}'
done
