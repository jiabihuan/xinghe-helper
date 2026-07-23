#!/usr/bin/env bash
set -euo pipefail
set -x

PHP_VERSION="${PHP_VERSION:-8.3.13}"
ANDROID_API="${ANDROID_API:-23}"
TARGET_ABI="${TARGET_ABI:-armeabi-v7a}"
ANDROID_NDK_HOME="${ANDROID_NDK_HOME:?ANDROID_NDK_HOME is required}"

ROOT_DIR="$(pwd)"
BUILD_DIR="$ROOT_DIR/build/php-runtime-build"
OUT_DIR="$ROOT_DIR/build/php-runtime-$TARGET_ABI"
ZIP_PATH="$ROOT_DIR/build/php-runtime-$TARGET_ABI.zip"
LOG_DIR="$ROOT_DIR/build/php-build-logs"

case "$TARGET_ABI" in
  armeabi-v7a)
    TARGET_HOST="arm-linux-androideabi"
    CLANG_TRIPLE="armv7a-linux-androideabi${ANDROID_API}"
    ;;
  arm64-v8a)
    TARGET_HOST="aarch64-linux-android"
    CLANG_TRIPLE="aarch64-linux-android${ANDROID_API}"
    ;;
  *)
    echo "Unsupported TARGET_ABI: $TARGET_ABI" >&2
    exit 1
    ;;
esac

TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
export PATH="$TOOLCHAIN/bin:$PATH"
export CC="${CLANG_TRIPLE}-clang"
export CXX="${CLANG_TRIPLE}-clang++"
export AR="llvm-ar"
export AS="llvm-as"
export LD="$CC"
export NM="llvm-nm"
export OBJDUMP="llvm-objdump"
export RANLIB="llvm-ranlib"
export STRIP="llvm-strip"
export CFLAGS="-fPIE -fPIC -O2"
export LDFLAGS="-pie"
export LIBS="-ldl -lm"
export PKG_CONFIG_LIBDIR="/dev/null"
export PKG_CONFIG_PATH=""
export ac_cv_c_bigendian_php=no
export ac_cv_func_malloc_0_nonnull=yes
export ac_cv_func_realloc_0_nonnull=yes
export ac_cv_func_memcmp_working=yes
export ac_cv_func_mmap_fixed_mapped=no
export php_cv_cc_rpath=no
export ac_cv_func_getpwnam=no
export ac_cv_func_getpwuid=no
export ac_cv_func_getgrgid=no
export ac_cv_func_getgrnam=no
export ac_cv_func_setpwent=no
export ac_cv_func_endpwent=no
export ac_cv_func_setgrent=no
export ac_cv_func_endgrent=no
export ac_cv_func_getgroups=no
export ac_cv_func_setgroups=no
export ac_cv_func_initgroups=no
export ac_cv_func_getlogin=no
export ac_cv_func_fork=no
export ac_cv_func_vfork=no
export ac_cv_func_daemon=no

rm -rf "$BUILD_DIR" "$OUT_DIR" "$ZIP_PATH" "$LOG_DIR"
mkdir -p "$BUILD_DIR" "$OUT_DIR/bin" "$OUT_DIR/etc" "$OUT_DIR/tmp" "$LOG_DIR"
cd "$BUILD_DIR"

if [[ ! -x "$TOOLCHAIN/bin/$CC" ]]; then
  echo "Android compiler not found: $TOOLCHAIN/bin/$CC" >&2
  exit 1
fi

"$CC" --version | tee "$LOG_DIR/compiler.log"

wget "https://www.php.net/distributions/php-${PHP_VERSION}.tar.gz"
tar -xzf "php-${PHP_VERSION}.tar.gz"
cd "php-${PHP_VERSION}"

set +e
timeout 25m ./configure \
  --host="$TARGET_HOST" \
  --build="$("./build/config.guess")" \
  --prefix="$OUT_DIR" \
  --disable-all \
  --enable-cli \
  --enable-cgi \
  --enable-filter \
  --enable-session \
  --enable-tokenizer \
  --enable-phar \
  --without-pcre-jit \
  --without-pear \
  --disable-cgi-fcgi \
  --disable-ipv6 2>&1 | tee "$LOG_DIR/configure.log"
configure_status="${PIPESTATUS[0]}"
set -e
if [[ "$configure_status" -ne 0 ]]; then
  echo "PHP configure failed with exit code $configure_status" >&2
  tail -n 200 "$LOG_DIR/configure.log" >&2 || true
  exit "$configure_status"
fi

if [[ "$TARGET_ABI" == "armeabi-v7a" || "$TARGET_ABI" == "arm64-v8a" ]]; then
  # Android's bionic resolver exposes the older res_search API, but not the
  # glibc-style res_nsearch/res_ninit struct API expected by PHP's DNS code.
  # PHP_CHECK_FUNC can still mark those symbols as available while
  # cross-compiling, so force the generated config back to the Android-safe
  # branch before make.
  for macro in HAVE_RES_NSEARCH HAVE_RES_NDESTROY HAVE_DN_SKIPNAME; do
    sed -i "s/^#define ${macro} 1$/\\/\\* #undef ${macro} \\*\\//" main/php_config.h
  done
  grep -E "HAVE_(RES_NSEARCH|RES_SEARCH|DN_SKIPNAME)" main/php_config.h | tee "$LOG_DIR/android-dns-config.log" || true
fi

set +e
timeout 45m make -j"$(nproc)" V=1 2>&1 | tee "$LOG_DIR/make.log"
make_status="${PIPESTATUS[0]}"
set -e
if [[ "$make_status" -ne 0 ]]; then
  echo "PHP make failed with exit code $make_status" >&2
  tail -n 200 "$LOG_DIR/make.log" >&2 || true
  exit "$make_status"
fi

if [[ -f sapi/cli/php ]]; then
  cp sapi/cli/php "$OUT_DIR/bin/php"
fi

if [[ -f sapi/cgi/php-cgi ]]; then
  cp sapi/cgi/php-cgi "$OUT_DIR/bin/php-cgi"
fi

if [[ ! -f "$OUT_DIR/bin/php" && ! -f "$OUT_DIR/bin/php-cgi" ]]; then
  echo "No PHP binary was produced" >&2
  exit 1
fi

cat > "$OUT_DIR/etc/php.ini" <<'EOF'
date.timezone=Asia/Shanghai
opcache.enable=0
opcache.enable_cli=0
default_socket_timeout=20
max_execution_time=55
EOF

chmod 700 "$OUT_DIR/bin/"* || true
"$STRIP" "$OUT_DIR/bin/"* || true

cd "$OUT_DIR"
zip -9 -r "$ZIP_PATH" .
ls -lh "$ZIP_PATH"
