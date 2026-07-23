#!/usr/bin/env bash
set -euo pipefail

PHP_VERSION="${PHP_VERSION:-8.3.13}"
ANDROID_API="${ANDROID_API:-23}"
TARGET_ABI="${TARGET_ABI:-armeabi-v7a}"
ANDROID_NDK_HOME="${ANDROID_NDK_HOME:?ANDROID_NDK_HOME is required}"

ROOT_DIR="$(pwd)"
BUILD_DIR="$ROOT_DIR/build/php-runtime-build"
OUT_DIR="$ROOT_DIR/build/php-runtime-$TARGET_ABI"
ZIP_PATH="$ROOT_DIR/build/php-runtime-$TARGET_ABI.zip"

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
export LD="ld.lld"
export RANLIB="llvm-ranlib"
export STRIP="llvm-strip"
export CFLAGS="-fPIE -fPIC -O2"
export LDFLAGS="-pie"
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

rm -rf "$BUILD_DIR" "$OUT_DIR" "$ZIP_PATH"
mkdir -p "$BUILD_DIR" "$OUT_DIR/bin" "$OUT_DIR/etc" "$OUT_DIR/tmp"
cd "$BUILD_DIR"

wget -q "https://www.php.net/distributions/php-${PHP_VERSION}.tar.gz"
tar -xzf "php-${PHP_VERSION}.tar.gz"
cd "php-${PHP_VERSION}"

./configure \
  --host="$TARGET_HOST" \
  --build="$("./build/config.guess")" \
  --prefix="$OUT_DIR" \
  --disable-all \
  --enable-cli \
  --enable-cgi \
  --enable-filter \
  --enable-json \
  --enable-session \
  --enable-tokenizer \
  --enable-phar \
  --with-pcre-jit=no \
  --without-pear \
  --disable-cgi-fcgi \
  --disable-ipv6

make -j"$(nproc)"

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
