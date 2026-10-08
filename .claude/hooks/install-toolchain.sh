#!/bin/bash
# Installs the toolchain this repository's build, tests and game recordings need, for Claude Code
# cloud sessions (run by session-start.sh) and for the image in docker/claude-cloud/Dockerfile:
#
#   - JDK 27 (Oracle build 27+35, the build .sdkmanrc and CI pin), checksum-verified, in /opt/jdk-27.
#     Oracle's archive is used because the cloud network policy allows download.oracle.com but not
#     SDKMAN, Adoptium or GitHub release downloads.
#   - arm-none-eabi-gcc + newlib, so GeneratedAsmToolchainTest assembles generated code instead of
#     skipping, and qemu-system-arm, to run the QEMU programs (juno/src/test/qemu) without Docker.
#   - ffmpeg and ImageMagick, to turn GameRecordingTest frames into videos and GIFs.
#
# Idempotent and non-interactive; needs root (cloud sessions and the Docker build run as root).
set -euo pipefail

JDK_DIR=/opt/jdk-27
JDK_URL_BASE=https://download.oracle.com/java/27/archive

packages=(curl ca-certificates gcc-arm-none-eabi libnewlib-arm-none-eabi qemu-system-arm ffmpeg imagemagick)
missing=()
for package in "${packages[@]}"; do
  if ! dpkg-query -W -f='${Status}' "$package" 2>/dev/null | grep -q "install ok installed"; then
    missing+=("$package")
  fi
done
if [ "${#missing[@]}" -gt 0 ]; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -qq
  apt-get install -y -qq --no-install-recommends "${missing[@]}" >/dev/null
fi

if [ ! -x "$JDK_DIR/bin/java" ]; then
  case "$(uname -m)" in
    x86_64) archive=jdk-27_linux-x64_bin.tar.gz
            sha256=0c7bb6c33c7fcc46674054cab734e3ca027ba05323c6c6ecbc8786f76a45b155 ;;
    aarch64) archive=jdk-27_linux-aarch64_bin.tar.gz
             sha256=$(curl -fsSL --retry 4 "$JDK_URL_BASE/$archive.sha256") ;;
    *) echo "Unsupported architecture $(uname -m) for JDK 27" >&2; exit 1 ;;
  esac
  tmp=$(mktemp -d)
  curl -fsSL --retry 4 -o "$tmp/$archive" "$JDK_URL_BASE/$archive"
  echo "$sha256  $tmp/$archive" | sha256sum -c --quiet -
  mkdir -p "$JDK_DIR"
  tar -xzf "$tmp/$archive" -C "$JDK_DIR" --strip-components=1
  rm -rf "$tmp"
fi
"$JDK_DIR/bin/java" -version 2>&1 | grep -q '"27' || { echo "$JDK_DIR is not a JDK 27" >&2; exit 1; }
