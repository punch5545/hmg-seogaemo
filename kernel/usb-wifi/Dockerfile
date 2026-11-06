# Build environment for the HMG-Seogaemo USB Wi-Fi kernel modules.
# The cross compiler (AOSP arm-eabi-4.7) is an x86-64 Linux binary, so the
# image is always linux/amd64, also on Apple Silicon hosts.
FROM --platform=linux/amd64 ubuntu:20.04@sha256:c664f8f86ed5a386b0a340d981b8f81714e21a8b9c73f658c4bea56aa179d54a

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    TZ=UTC

# Host tools for a Linux 3.18 kernel tree (Kconfig, fixdep, modpost) plus
# fetch/verify utilities. Ubuntu 20.04 keeps GCC 9, which still builds the
# 3.18 host programs without patches.
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      bc bison build-essential ca-certificates curl file flex git \
      kmod perl xz-utils \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /usb-wifi
