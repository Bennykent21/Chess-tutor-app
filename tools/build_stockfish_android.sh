#!/usr/bin/env bash
# Builds Stockfish for Android with its neural network embedded, and installs it as
# app/src/main/jniLibs/<abi>/libstockfish.so.
#
# - Official Stockfish source, pinned to a release tag AND its exact commit.
# - The network is downloaded by Stockfish's own build and checked against its SHA-256.
# - Targets Android 7 (API 24), the app's minSdk, so the compiler rejects any newer API.
# - Linked with 16 KB page alignment, which Google Play requires for Android 15+.
# - Verifies alignment, the embedded network, and the dynamic dependencies before
#   installing anything. Any failed check stops the build.
#
# Used by GitHub Actions. Also runs on Linux or WSL:
#   ANDROID_NDK_HOME=/path/to/ndk tools/build_stockfish_android.sh
#
# Stockfish is GPL-3.0. Its source for this exact build is the pinned commit below.
set -euo pipefail

SF_TAG="${SF_TAG:-sf_19}"
SF_COMMIT="${SF_COMMIT:-edb0d9db6731067ec50ce619ff372b463bc4dd5d}"
ANDROID_API="${ANDROID_API:-24}"
ABIS="${ABIS:-arm64-v8a x86_64}"
PAGE_SIZE=16384
READELF="${READELF:-llvm-readelf}"

# verify_binary <file> <abi> <network-bytes>: fails unless the binary is 16 KB aligned,
# carries the network, and needs only libraries Android provides.
verify_binary() {
  local bin="$1" abi="$2" net_bytes="$3" size align needed lib
  size="$(stat -c %s "$bin")"
  for align in $("$READELF" -lW "$bin" | awk '$1=="LOAD" { print $NF }'); do
    if [ $((align)) -lt "$PAGE_SIZE" ]; then
      echo "error: $abi has a LOAD segment aligned to $align, below 16 KB" >&2
      return 1
    fi
  done
  if [ "$size" -le "$net_bytes" ]; then
    echo "error: $abi binary ($size bytes) is not larger than the network ($net_bytes bytes): network not embedded" >&2
    return 1
  fi
  needed="$("$READELF" -dW "$bin" | awk '/NEEDED/ { gsub(/[\[\]]/, "", $NF); print $NF }' | sort | tr '\n' ' ')"
  for lib in $needed; do
    case "$lib" in
      libc.so|libm.so|libdl.so|liblog.so) ;;
      *) echo "error: $abi depends on $lib, which Android does not provide" >&2; return 1 ;;
    esac
  done
  echo "$abi: $((size / 1024 / 1024)) MB, 16 KB aligned, needs: $needed"
}

# check_arm64_paths <file>: runs the binary on an emulated old CPU and a modern one. Each must
# work, and both must search exactly the same nodes: the faster path cannot change a verdict.
check_arm64_paths() {
  local bin="$1" qemu old new old_arch new_arch
  qemu="$(command -v qemu-aarch64-static || command -v qemu-aarch64 || true)"
  if [ -z "$qemu" ]; then
    if [ -n "${CI:-}" ]; then
      echo "error: qemu-user-static is needed to check the arm64 binary" >&2
      return 1
    fi
    echo "warning: no qemu-aarch64, so the arm64 binary was not run" >&2
    return 0
  fi
  old_arch="$("$qemu" -cpu cortex-a53 "$bin" compiler 2>&1 | awk -F: '/Compilation architecture/ { gsub(/[[:space:]]/, "", $2); print $2; exit }')"
  new_arch="$("$qemu" -cpu cortex-a76 "$bin" compiler 2>&1 | awk -F: '/Compilation architecture/ { gsub(/[[:space:]]/, "", $2); print $2; exit }')"
  echo "paths taken: old CPU $old_arch, modern CPU $new_arch"
  if [ "$old_arch" != "armv8" ] || [ "$new_arch" != "armv8-dotprod" ]; then
    echo "error: expected armv8 and armv8-dotprod" >&2
    return 1
  fi
  old="$("$qemu" -cpu cortex-a53 "$bin" bench 16 1 10 2>&1 | awk '/^Nodes searched/ { print $NF }')"
  new="$("$qemu" -cpu cortex-a76 "$bin" bench 16 1 10 2>&1 | awk '/^Nodes searched/ { print $NF }')"
  echo "bench nodes: plain $old, dot product $new"
  if [ -z "$old" ] || [ "$old" != "$new" ]; then
    echo "error: the two paths disagree (plain '$old', dot product '$new')" >&2
    return 1
  fi
}

if [ "${1:-}" = "--verify" ]; then
  # tools/build_stockfish_android.sh --verify <file> <abi> <network-bytes>
  verify_binary "$2" "$3" "$4"
  exit $?
fi

OUT_DIR="${1:-app/src/main/jniLibs}"
INFO_FILE="${2:-stockfish-build/STOCKFISH_BUILD.txt}"

NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_LATEST_HOME:-}}"
if [ -z "$NDK" ] || [ ! -d "$NDK" ]; then
  echo "error: set ANDROID_NDK_HOME to an Android NDK (r27c or newer)" >&2
  exit 1
fi
HOST_TAG="linux-x86_64"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"
[ -d "$TOOLCHAIN" ] || { echo "error: NDK toolchain not found at $TOOLCHAIN" >&2; exit 1; }
export PATH="$TOOLCHAIN:$PATH"
echo "NDK: $NDK ($(grep -m1 Pkg.Revision "$NDK/source.properties" 2>/dev/null || echo unknown))"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

git clone --quiet --depth 1 --branch "$SF_TAG" https://github.com/official-stockfish/Stockfish.git "$WORK/sf"
ACTUAL_COMMIT="$(git -C "$WORK/sf" rev-parse HEAD)"
if [ "$ACTUAL_COMMIT" != "$SF_COMMIT" ]; then
  echo "error: $SF_TAG is $ACTUAL_COMMIT, expected $SF_COMMIT. Refusing to build." >&2
  exit 1
fi
SRC="$WORK/sf/src"

# Fetch and verify the network once (Stockfish's script checks the SHA-256 in its name).
# On a slow connection, download the network separately and set SF_NET_DIR to its folder:
# Stockfish's check still runs, and skips the download when the file is valid.
if [ -n "${SF_NET_DIR:-}" ]; then
  cp "$SF_NET_DIR"/nn-*.nnue "$SRC"/
fi
make -C "$SRC" net
NET_FILE="$(ls "$SRC"/nn-*.nnue | head -n1)"
NET_BYTES="$(stat -c %s "$NET_FILE")"
echo "Network: $(basename "$NET_FILE") ($((NET_BYTES / 1024 / 1024)) MB)"

build_abi() {
  local abi="$1" arch triple
  case "$abi" in
    # One binary and one network, with a plain and a dot-product build of the engine inside.
    # It picks at start-up: dot product makes the network much faster on phones that have it.
    arm64-v8a) arch="arm64-universal"; triple="aarch64-linux-android" ;;
    x86_64) arch="x86-64-sse41-popcnt"; triple="x86_64-linux-android" ;;
    *) echo "error: unsupported ABI $abi" >&2; exit 1 ;;
  esac

  echo "::group::Build Stockfish for $abi ($arch, API $ANDROID_API)"
  make -C "$SRC" objclean
  make -C "$SRC" -j"$(nproc)" build \
    ARCH="$arch" COMP=ndk \
    COMPCXX="${triple}${ANDROID_API}-clang++" \
    EXTRALDFLAGS="-Wl,-z,max-page-size=$PAGE_SIZE -Wl,-z,common-page-size=$PAGE_SIZE"
  llvm-strip "$SRC/stockfish"
  echo "::endgroup::"

  verify_binary "$SRC/stockfish" "$abi" "$NET_BYTES"
  if [ "$abi" = "arm64-v8a" ]; then check_arm64_paths "$SRC/stockfish"; fi

  mkdir -p "$OUT_DIR/$abi"
  cp "$SRC/stockfish" "$OUT_DIR/$abi/libstockfish.so"
}

for abi in $ABIS; do
  build_abi "$abi"
done

mkdir -p "$(dirname "$INFO_FILE")"
{
  echo "Stockfish $SF_TAG"
  echo "Source: https://github.com/official-stockfish/Stockfish/tree/$SF_COMMIT"
  echo "Network: $(basename "$NET_FILE") (sha256 $(sha256sum "$NET_FILE" | cut -d' ' -f1))"
  echo "Android API: $ANDROID_API, page size: $PAGE_SIZE"
  for abi in $ABIS; do
    echo "$abi: sha256 $(sha256sum "$OUT_DIR/$abi/libstockfish.so" | cut -d' ' -f1)"
  done
} > "$INFO_FILE"
cat "$INFO_FILE"
