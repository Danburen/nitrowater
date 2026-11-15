#!/usr/bin/env bash
# ============================================================
# Piston 语言包安装（直接往 /piston 卷里放包，不经过 piston 的 API）
#
# 为什么不用 POST /api/v2/packages：它每次都要去 GitHub 拉包索引，跨境拉不动时会把
# piston 的 API 进程直接打死（curl: (52) Empty reply，之后还得 docker restart）。
# 这里改成三步：下官方 tarball → 解到 /piston/packages/<包>/<版本>/ → 补 .ppman-installed。
# piston 启动时只注册带这个标记的目录，所以标记不能少。
#
# 对已安装的适配：卷里已经装了某个版本时，默认**不重复装**（打印 installed: <版本>）；
# 只有 --upgrade 才会去装索引里的最新版（旧版本目录保留，需要时手动删）。
# 目录存在但没有 .ppman-installed（比如上次被 Ctrl-C 打断）会就地重装。
#
# 用法（在宿主机任意目录执行，只需要 docker）：
#   bash deploy/bin/install-piston-runtimes.sh                    # 装缺的默认 6 个，已装的跳过
#   bash deploy/bin/install-piston-runtimes.sh rust               # 没装过才装（最新版）
#   bash deploy/bin/install-piston-runtimes.sh rust:1.72.0 php    # 指定版本
#   bash deploy/bin/install-piston-runtimes.sh --upgrade          # 有新版才装（旧版保留）
#   bash deploy/bin/install-piston-runtimes.sh --list             # 列出索引里的包和版本
#   bash deploy/bin/install-piston-runtimes.sh --dry-run          # 只打印将要做什么
#   bash deploy/bin/install-piston-runtimes.sh --no-restart       # 装完不重启 piston、不校验
#
# 环境变量：
#   PISTON_CONTAINER  容器名（默认 nitrowater-piston）
#   PISTON_MIRROR     下载前缀（默认 https://gh-proxy.org/，拼在原始 GitHub 地址前面）
#   PISTON_DL_DIR     下载缓存目录（断点续传用，默认 /var/tmp/piston-dl）
#
# 幂等：已装的包不会重下；下载中断后重跑从断点续传（wget -c）。
# 退出码：0 = 请求的包都在位并已注册；1 = 有包失败（其余照装）；2 = 参数/环境有问题。
# ============================================================
set -euo pipefail

PISTON_CONTAINER="${PISTON_CONTAINER:-nitrowater-piston}"
PISTON_MIRROR="${PISTON_MIRROR:-https://gh-proxy.org/}"
PISTON_DL_DIR="${PISTON_DL_DIR:-/var/tmp/piston-dl}"

# 门户默认需要的 6 个包；gcc 一个包提供 c/c++/d/fortran，node 提供 javascript
DEFAULT_PACKAGES=(java gcc node python typescript lua)

GH_PKGS="https://github.com/engineer-man/piston/releases/download/pkgs"

LIST=0
DRY=0
UPGRADE=0
RESTART=1
PACKAGES=()

usage() {
    cat <<'EOF'
Usage: bash deploy/bin/install-piston-runtimes.sh [options] [package[:version] ...]

  (no argument)      ensure the default set: java gcc node python typescript lua
  <package>          install only if that package is missing (highest version in the index)
  <package>:<ver>    install that exact version unless it is already there
  --upgrade          also install the highest index version when an older one is present
  --list             list packages and versions available in the index
  --dry-run          show what would be installed, change nothing
  --no-restart       do not restart piston / skip the runtime verification
  -h, --help         show this help

Env: PISTON_CONTAINER (nitrowater-piston), PISTON_MIRROR (https://gh-proxy.org/),
     PISTON_DL_DIR (/var/tmp/piston-dl).

Already installed packages are skipped (a directory is "installed" when it carries the
.ppman-installed marker). --upgrade keeps the old version directory on disk.
On the host: docker only. Exit code 0 = every requested package is present and registered.
EOF
    exit 0
}

while [ $# -gt 0 ]; do
    case "$1" in
        --list)       LIST=1; shift ;;
        --dry-run)    DRY=1; shift ;;
        --upgrade)    UPGRADE=1; shift ;;
        --no-restart) RESTART=0; shift ;;
        -h|--help)    usage ;;
        --)           shift; PACKAGES+=("$@"); break ;;
        -*)           echo "ERROR: unknown option: $1" >&2; exit 2 ;;
        *)            PACKAGES+=("$1"); shift ;;
    esac
done
[ "${#PACKAGES[@]}" -gt 0 ] || PACKAGES=("${DEFAULT_PACKAGES[@]}")

command -v docker >/dev/null 2>&1 || { echo "ERROR: docker not found" >&2; exit 2; }

if [ "$LIST" -eq 1 ]; then
    docker run --rm -e URL="$PISTON_MIRROR$GH_PKGS/index" alpine sh -c \
        'wget -q -O - "$URL" | awk -F, "NF>=4 {printf \"%-12s %s\n\", \$1, \$2}" | sort -k1,1'
    exit 0
fi

docker inspect "$PISTON_CONTAINER" >/dev/null 2>&1 || {
    echo "ERROR: container '$PISTON_CONTAINER' not found (start it with docker compose first)" >&2
    exit 2
}

# Volume name carries the compose project prefix (e.g. nitrowater-app_nitrowater-piston).
VOL="$(docker inspect "$PISTON_CONTAINER" --format '{{range .Mounts}}{{if eq .Destination "/piston"}}{{.Name}}{{end}}{{end}}')"
[ -n "$VOL" ] || { echo "ERROR: no volume mounted at /piston on '$PISTON_CONTAINER'" >&2; exit 2; }

mkdir -p "$PISTON_DL_DIR"

echo "==> container=${PISTON_CONTAINER} volume=${VOL}"
echo "==> mirror=${PISTON_MIRROR} upgrade=${UPGRADE}"
echo "==> packages=${PACKAGES[*]}"

# All work happens inside a throwaway alpine container: it has wget/tar and lives in
# the same docker, so the host needs nothing beyond the docker CLI.
# -i is required: the script itself arrives on stdin through the heredoc.
set +e
docker run --rm -i -v "$VOL":/piston -v "$PISTON_DL_DIR":/dl \
    -e URL_BASE="$PISTON_MIRROR$GH_PKGS" -e DRY="$DRY" -e UPGRADE="$UPGRADE" \
    alpine sh -s -- "${PACKAGES[@]}" <<'INNER'
set -u
cd /dl || exit 1

fail_total=0
index_ready=0

fetch_index() {
    [ "$index_ready" -eq 1 ] && return 0
    if wget -q -O index.new "$URL_BASE/index"; then
        mv index.new index
        index_ready=1
        return 0
    fi
    rm -f index.new
    return 1
}

# Highest version for a package: the index is only sorted lexically per release,
# so compare with `sort -V` (busybox supports it) instead of trusting the last line.
latest_version() {
    awk -F, -v p="$1" '$1 == p {print $2}' index | sort -V | tail -n 1
}

# Highest version already on disk that carries the marker (empty when nothing usable).
installed_version() {
    for d in /piston/packages/"$1"/*/; do
        [ -f "${d}.ppman-installed" ] || continue
        basename "$d"
    done | sort -V | tail -n 1
}

# Directory present but no marker: a previous run was interrupted, so reinstall it.
has_partial() {
    [ -d "$1" ] || return 1
    [ -f "$1/.ppman-installed" ] && return 1
    return 0
}

for spec in "$@"; do
    pkg="${spec%%:*}"
    ver=""
    case "$spec" in *:*) ver="${spec#*:}" ;; esac

    have="$(installed_version "$pkg")"

    if [ -n "$ver" ]; then
        # Pinned version: only this exact directory matters.
        if [ -f "/piston/packages/$pkg/$ver/.ppman-installed" ]; then
            echo "skip    $pkg-$ver (installed)"
            continue
        fi
    else
        # No version given: never touch an existing installation unless --upgrade.
        if [ -n "$have" ] && [ "$UPGRADE" != "1" ]; then
            echo "skip    $pkg (installed: $have, use --upgrade to move on)"
            continue
        fi
        if ! fetch_index; then
            echo "FAILED  $pkg (index unreachable via $URL_BASE)"
            fail_total=$((fail_total + 1))
            continue
        fi
        ver="$(latest_version "$pkg")"
        if [ -z "$ver" ]; then
            echo "FAILED  $pkg (not in index)"
            fail_total=$((fail_total + 1))
            continue
        fi
        if [ -f "/piston/packages/$pkg/$ver/.ppman-installed" ]; then
            echo "skip    $pkg-$ver (up to date)"
            continue
        fi
        [ -n "$have" ] && echo "upgrade $pkg: $have -> $ver (old dir kept)"
    fi

    dir="/piston/packages/$pkg/$ver"
    file="/dl/$pkg-$ver.pkg.tar.gz"

    if has_partial "$dir"; then
        echo "warn    $pkg-$ver has no .ppman-installed (interrupted?), reinstalling"
    fi
    if [ "$DRY" = "1" ]; then
        echo "would   $pkg-$ver  <- $URL_BASE/$pkg-$ver.pkg.tar.gz"
        continue
    fi

    echo "==>     $pkg-$ver"
    if ! wget -c -O "$file" "$URL_BASE/$pkg-$ver.pkg.tar.gz"; then
        echo "FAILED  $pkg-$ver (download)"
        fail_total=$((fail_total + 1))
        continue
    fi
    if rm -rf "$dir" && mkdir -p "$dir" && tar xzf "$file" -C "$dir"; then
        date +%s > "$dir/.ppman-installed"
        rm -f "$file"
        echo "ok      $pkg-$ver"
    else
        rm -rf "$dir" "$file"
        echo "FAILED  $pkg-$ver (extract)"
        fail_total=$((fail_total + 1))
    fi
done

exit "$fail_total"
INNER
INSTALL_RC=$?
set -e

if [ "$DRY" -eq 1 ]; then
    echo "==> dry-run: nothing changed"
    [ "$INSTALL_RC" -eq 0 ] || exit 1
    exit 0
fi

fetch_runtimes() {
    docker exec "$PISTON_CONTAINER" node -e \
        "require('http').get('http://127.0.0.1:2000/api/v2/runtimes',r=>{let d='';r.on('data',c=>d+=c);r.on('end',()=>process.stdout.write(d))}).on('error',()=>process.exit(1))"
}

if [ "$RESTART" -eq 1 ]; then
    echo "==> restart ${PISTON_CONTAINER}"
    docker restart "$PISTON_CONTAINER" >/dev/null

    tries=0
    until fetch_runtimes >/dev/null 2>&1; do
        tries=$((tries + 1))
        if [ "$tries" -ge 30 ]; then
            echo "ERROR: ${PISTON_CONTAINER} did not answer on :2000 after restart" >&2
            exit 1
        fi
        sleep 2
    done
fi

RUNTIMES="$(fetch_runtimes || true)"

# Package name is not the runtime name: gcc registers c/c++/d/fortran, node registers
# javascript. Everything else registers under its own name.
expected_language() {
    case "$1" in
        gcc)  printf 'c' ;;
        node) printf 'javascript' ;;
        *)    printf '%s' "$1" ;;
    esac
}

echo "==> runtimes"
printf '%s\n' "$RUNTIMES"

missing=0
for pkg in "${PACKAGES[@]}"; do
    pkg="${pkg%%:*}"
    lang="$(expected_language "$pkg")"
    if printf '%s' "$RUNTIMES" | grep -q "\"language\":\"$lang\""; then
        echo "    ok      ${lang}  (package ${pkg})"
    else
        echo "    MISSING ${lang}  (package ${pkg}) -- check docker logs ${PISTON_CONTAINER}"
        missing=$((missing + 1))
    fi
done

if [ "$INSTALL_RC" -ne 0 ]; then
    echo "ERROR: ${INSTALL_RC} package(s) failed to install" >&2
    exit 1
fi
if [ "$missing" -ne 0 ]; then
    echo "ERROR: ${missing} package(s) installed but not registered" >&2
    exit 1
fi

echo "==> done"
