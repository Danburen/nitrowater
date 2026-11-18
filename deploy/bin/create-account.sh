#!/usr/bin/env bash
# ============================================================
# create-account.sh - create (or reset) a username + password SSO account
#
# The ops counterpart of portal registration: a hand-made account that has a
# password and no phone/email. MySQL cannot compute BCrypt, so this script turns
# the plaintext password into the digest the service expects and lib/create-account.sql
# writes the rows (user + user_data + user_role).
#
# Parity with the Java side (org.springframework.security.crypto.bcrypt):
#   - same algorithm: bcrypt over the password's UTF-8 bytes, 16-byte salt, 23-byte
#     digest, truncated at 72 bytes - both implementations do exactly this;
#   - cost pinned to 10 (`-C 10`) so it matches BCryptPasswordEncoder's default;
#     verification would work at any cost, but a cost-5 digest (htpasswd's default)
#     would be the weakest credential in the database;
#   - the digest is shape-checked before it is written, so a broken tool fails loudly
#     instead of storing an unusable credential;
#   - the password alphabet is printable ASCII, so the bytes cannot differ between
#     the tool and the JVM (a GBK console vs UTF-8 Java is the one real divergence);
#   - `$2a$` / `$2b$` / `$2y$` are all accepted by the encoder; BcryptInteropTest in
#     nitrowater-account pins that against a digest produced by htpasswd itself.
#
# Usage:
#   bash deploy/bin/create-account.sh --username ops-admin --role ADMIN
#   bash deploy/bin/create-account.sh -u ops-admin --force      # reset its password
#   printf '%s' "$PASS" | bash deploy/bin/create-account.sh -u ops-admin
#
# The password is typed twice at a hidden prompt (echo off, like `passwd`), or piped
# in on stdin - never taken from argv, so it stays out of shell history and `ps`.
#
# Requires one of: htpasswd (apache2-utils), python3 + bcrypt, or docker (pulls
# httpd:alpine once). On a deployed host the SQL runs inside the mysql container
# (no client needed, like backup_mysql.sh); on a dev box the local `mysql` client
# is used. Credentials come from the environment or deploy/docker/.env.
#
# Exit codes: 0 = account written; 1 = refused (validation, duplicate, no AES key).
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$DEPLOY_ROOT/deploy/docker/.env"
SECRETS_FILE="$DEPLOY_ROOT/deploy/docker/.env.prod.secrets"
SQL_FILE="$SCRIPT_DIR/lib/create-account.sql"

MYSQL_CONTAINER="${MYSQL_CONTAINER:-nitrowater-mysql}"
DATABASE="nitrowater_account"

die() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

usage() {
    # Header comment only (2-36): line 37 is the closing banner, 38+ is code.
    sed -n '2,36p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
}

# Read one KEY without sourcing .env: values may contain '#', '$' or spaces.
# Later files win, matching `docker compose --env-file` precedence.
env_get() {
    local key="$1" file line value=""
    for file in "$ENV_FILE" "$SECRETS_FILE"; do
        [ -f "$file" ] || continue
        line="$(sed -n "s/^[[:space:]]*${key}=//p" "$file" | tail -n 1)"
        [ -n "$line" ] && value="${line%$'\r'}"
    done
    printf '%s' "$value"
}

USERNAME=""
ROLE="USER"
ROLE_GIVEN=0
FORCE=0

while [ $# -gt 0 ]; do
    case "$1" in
        -u | --username)
            [ $# -ge 2 ] || die "$1 needs a value"
            USERNAME="$2"
            shift 2
            ;;
        --username=*)
            USERNAME="${1#*=}"
            shift
            ;;
        -r | --role)
            [ $# -ge 2 ] || die "$1 needs a value"
            ROLE="$2"
            ROLE_GIVEN=1
            shift 2
            ;;
        --role=*)
            ROLE="${1#*=}"
            ROLE_GIVEN=1
            shift
            ;;
        -f | --force)
            FORCE=1
            shift
            ;;
        -h | --help)
            usage
            ;;
        *)
            die "unknown argument: $1 (see --help)"
            ;;
    esac
done

[ -n "$USERNAME" ] || die "--username is required (see --help)"

# Mirror the server-side rules (RegisterRequest + UsernameValidator +
# AuthIdentifierUtil): a hand-made account must not be one the app would have
# refused, and must not be phone-shaped or phone login would resolve it by hash.
[[ "$USERNAME" =~ ^[a-zA-Z0-9_]{4,20}$ ]] ||
    die "username must be 4-20 characters of letters, digits or underscore"
[[ "$USERNAME" =~ ^1[3-9][0-9]{9}$ ]] &&
    die "username must not look like a mainland mobile number (login resolves phones by hash)"

ROLE="$(printf '%s' "$ROLE" | tr '[:lower:]' '[:upper:]')"
case "$ROLE" in
    USER | ADMIN | SUPER_ADMIN) ;;
    *) die "role must be USER, ADMIN or SUPER_ADMIN (got: $ROLE)" ;;
esac

# StrongPassword: ^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,20}$ - lookaheads are not
# portable to bash's ERE, so the rule is spelled out.
read_password() {
    # A pipe or a redirected file carries the value: take it verbatim (a pipe echoes nothing).
    if [ ! -t 0 ] && { [ -p /dev/stdin ] || [ -f /dev/stdin ]; }; then
        cat
        return
    fi
    # Not a pipe, so ask the terminal - even when stdin is not reported as a TTY, because `cat`
    # there would sit waiting for EOF with the password echoed on screen. No terminal at all
    # (CI, cron) means stdin is all we have.
    if ! { true </dev/tty; } 2>/dev/null; then
        cat
        return
    fi
    exec </dev/tty
    local first second
    # -s turns the terminal's echo off: the password is never displayed, not even as stars.
    printf 'Password for %s: ' "$USERNAME" >&2
    IFS= read -r -s first || die "could not read the password from the terminal (Ctrl-D?)"
    printf '\n' >&2
    printf 'Repeat password: ' >&2
    IFS= read -r -s second || die "could not read the password from the terminal (Ctrl-D?)"
    printf '\n' >&2
    [ "$first" = "$second" ] || die "passwords do not match"
    printf '%s' "$first"
}

PASSWORD="$(read_password)"
[ -n "$PASSWORD" ] || die "empty password"
[ "${#PASSWORD}" -ge 8 ] && [ "${#PASSWORD}" -le 20 ] ||
    die "password must be 8-20 characters long"
[[ "$PASSWORD" =~ [a-z] ]] && [[ "$PASSWORD" =~ [A-Z] ]] && [[ "$PASSWORD" =~ [0-9] ]] ||
    die "password must contain a lowercase letter, an uppercase letter and a digit"
# Printable ASCII only (no space): the digest is over the password's bytes, and the tool that
# computes it sees whatever the console/pipe hands over (a Windows console may be GBK while the
# service hashes UTF-8). Restricting the alphabet removes that class of mismatch entirely.
leftover="$(printf '%s' "$PASSWORD" | LC_ALL=C tr -d '!-~' | LC_ALL=C wc -c)"
[ "$leftover" -eq 0 ] ||
    die "password must be printable ASCII (letters, digits, punctuation; no spaces or non-ASCII)"

# BCrypt cost 10 to match Spring's default: a cost-5 digest (htpasswd's default)
# would still verify, but it would be the weakest credential in the database.
bcrypt_hash() {
    local pw="$1"
    if command -v htpasswd >/dev/null 2>&1; then
        # -i reads the password from stdin (no argv leak), -C sets the cost.
        printf '%s\n' "$pw" | htpasswd -inBC 10 nitrowater | cut -d: -f2
    elif command -v python3 >/dev/null 2>&1 && python3 -c 'import bcrypt' >/dev/null 2>&1; then
        printf '%s' "$pw" |
            python3 -c 'import sys, bcrypt; print(bcrypt.hashpw(sys.stdin.buffer.read(), bcrypt.gensalt(rounds=10)).decode())'
    elif command -v docker >/dev/null 2>&1; then
        printf '%s\n' "$pw" |
            docker run --rm -i --entrypoint htpasswd httpd:2-alpine -inBC 10 nitrowater | cut -d: -f2
    else
        die "need htpasswd (apache2-utils), python3 with the bcrypt module, or docker"
    fi
}

HASH="$(bcrypt_hash "$PASSWORD")" || die "could not compute the BCrypt digest"
unset PASSWORD
# Shape check before the value is embedded in the SQL: a digest is [./A-Za-z0-9$]
# only, so after this test single-quoting it is safe by construction.
[[ "$HASH" =~ ^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$ ]] ||
    die "unexpected BCrypt output (want \$2a/\$2b/\$2y\$COST\$53 chars)"

[ -f "$SQL_FILE" ] || die "missing $SQL_FILE"

# Deployment: run inside the mysql container (no client on the host, no TCP hop).
# Dev box: no running container by that name, so use whatever `mysql` is on PATH.
MYSQL_MODE="container"
if [ "$(docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER" 2>/dev/null || true)" != "true" ]; then
    command -v mysql >/dev/null 2>&1 ||
        die "container '$MYSQL_CONTAINER' is not running and there is no mysql client on PATH"
    MYSQL_MODE="host"
fi

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USERNAME:-$(env_get DB_USERNAME)}"
DB_PASS="${DB_PASSWORD:-$(env_get DB_PASSWORD)}"
if [ -z "$DB_USER" ] || [ -z "$DB_PASS" ]; then
    DB_USER="${DB_USER:-root}"
    DB_PASS="${DB_PASS:-$(env_get MYSQL_ROOT_PASSWORD)}"
fi
[ -n "$DB_PASS" ] ||
    die "no MySQL password: set DB_PASSWORD (dev, see AGENTS.md) or put it in $ENV_FILE"

# One client call, stdout only. MYSQL_PWD keeps the password out of the host's and
# the container's process list.
mysql_exec() {
    local database="$1"
    shift
    if [ "$MYSQL_MODE" = "container" ]; then
        docker exec -i -e MYSQL_PWD="$DB_PASS" "$MYSQL_CONTAINER" \
            mysql -u "$DB_USER" -h 127.0.0.1 --default-character-set=utf8mb4 "$@" "$database"
    else
        MYSQL_PWD="$DB_PASS" mysql -u "$DB_USER" -h "$DB_HOST" -P "$DB_PORT" \
            --default-character-set=utf8mb4 "$@" "$database"
    fi
}

# Pre-flight in a single query: existing uid, role id, active AES key, and whoever
# already owns the uid we would derive. Doing this here (instead of inside the SQL)
# lets the messages be precise: MySQL cannot RAISE a custom error outside a stored
# program, and creating one would need CREATE ROUTINE that the app user may lack.
preflight_sql="SELECT
  COALESCE((SELECT uid FROM \`user\` WHERE username = '$USERNAME' LIMIT 1), ''),
  COALESCE((SELECT id FROM \`role\` WHERE code = '$ROLE' LIMIT 1), ''),
  COALESCE((SELECT key_id FROM encryption_data_key
             WHERE key_purpose = 'AES_DATA' AND key_status = 1
             ORDER BY created_at DESC, id DESC LIMIT 1), ''),
  (SELECT 9000000000 + CRC32(LOWER('$USERNAME')) % 99999999),
  COALESCE((SELECT username FROM \`user\`
             WHERE uid = (SELECT 9000000000 + CRC32(LOWER('$USERNAME')) % 99999999) LIMIT 1), '')"

preflight="$(mysql_exec "$DATABASE" --batch --skip-column-names -e "$preflight_sql")" ||
    die "pre-flight query failed (check credentials and that the database exists)"

# Do not `read` with IFS=$'\t': tab is IFS *whitespace*, so bash drops the empty
# leading field on the create path ("no such account" / "uid free") and every
# value shifts one column left. Split on tab in awk instead, which keeps empties,
# and drop the \r a Windows client appends.
preflight_field() {
    printf '%s\n' "$preflight" | tr -d '\r' | awk -F'\t' -v n="$1" '{print $n; exit}'
}

existing_uid="$(preflight_field 1)"
role_id="$(preflight_field 2)"
aes_key_id="$(preflight_field 3)"
derived_uid="$(preflight_field 4)"
uid_owner="$(preflight_field 5)"

[ -n "$role_id" ] || die "role '$ROLE' does not exist in this database"
[ -n "$aes_key_id" ] ||
    die "no ACTIVE AES_DATA key - start nitrowater-account once so it bootstraps one"
[ -n "$derived_uid" ] || die "could not derive a uid for '$USERNAME'"

# Everything that reaches the SQL below as a literal is shape-checked first: username, role and
# digest are validated above, the ids come from MySQL as digits, and the key id is stored as a
# string, so it is checked here rather than trusted.
[[ "$role_id" =~ ^[0-9]{1,20}$ ]] || die "unexpected role id: $role_id"
[[ "$derived_uid" =~ ^[0-9]{1,20}$ ]] || die "unexpected uid: $derived_uid"
[[ -z "$existing_uid" || "$existing_uid" =~ ^[0-9]{1,20}$ ]] || die "unexpected uid: $existing_uid"
[[ "$aes_key_id" =~ ^[-A-Za-z0-9_.:]{1,64}$ ]] || die "unexpected encryption key id: $aes_key_id"

if [ -n "$existing_uid" ]; then
    [ "$FORCE" -eq 1 ] ||
        die "account '$USERNAME' already exists - re-run with --force to reset its password"
    uid="$existing_uid"
else
    # Derived in MySQL (CRC32 of the lowercased name), so the ownership check above
    # and the insert below always agree on the number.
    uid="$derived_uid"
    [ -z "$uid_owner" ] ||
        die "uid $uid is already used by '$uid_owner' - choose a different username"
fi

# Reset leaves existing roles alone unless --role was given explicitly; a new
# account always gets the requested one.
grant_role=0
[ -z "$existing_uid" ] && grant_role=1
[ "$ROLE_GIVEN" -eq 1 ] && grant_role=1

printf 'writing %s (uid %s, %s%s)\n' "$USERNAME" "$uid" \
    "$([ "$grant_role" -eq 1 ] && printf 'role %s' "$ROLE" || printf 'roles unchanged')" \
    "$([ "$FORCE" -eq 1 ] && printf ', force' || true)" >&2

# The four SET statements are the only place user input reaches SQL; username,
# hash and role were validated above, uid is numeric, so quoting is enough and no
# bind layer is needed.
{
    printf "SET @uid = %s;\n" "$uid"
    printf "SET @username = '%s';\n" "$USERNAME"
    printf "SET @password_hash = '%s';\n" "$HASH"
    printf "SET @role_id = %s;\n" "$role_id"
    printf "SET @aes_key_id = '%s';\n" "$aes_key_id"
    printf 'SET @grant_role = %s;\n' "$grant_role"
    cat "$SQL_FILE"
} | mysql_exec "$DATABASE" --table

printf 'done: %s signs in with its password (no phone/email bound)\n' "$USERNAME" >&2
