#!/bin/sh
set -e

# Remember whether PUID/PGID were set at all, before applying the defaults.
IDS_SET="${PUID+x}${PGID+x}"
PUID="${PUID:-1000}"
PGID="${PGID:-1000}"

# Images published before the rename from CommaFeed to MadnessFeed kept their data in /commafeed/data. If a volume is
# still mounted there, keep using it (the database lives in ./data, relative to the working directory) so that
# upgrading doesn't silently start from an empty database.
DATA_DIR=/madnessfeed/data
if [ -d /commafeed/data ]; then
    echo "entrypoint: found a volume mounted at the old /commafeed/data path, using it. Mount it at /madnessfeed/data instead when convenient." >&2
    DATA_DIR=/commafeed/data
    cd /commafeed
fi

if [ "$(id -u)" != "0" ]; then
    # Started with docker's --user: already unprivileged, nothing to switch.
    if [ -n "$IDS_SET" ]; then
        echo "entrypoint: running as $(id -u):$(id -g) (set with --user), PUID/PGID are ignored" >&2
    fi
    exec "$@"
fi

check_id() {
    case "$2" in
        '' | *[!0-9]*)
            echo "entrypoint: $1 must be a whole number (e.g. 99 or 100), got '$2'" >&2
            exit 1
            ;;
    esac
    if [ "$2" -eq 0 ]; then
        echo "entrypoint: $1=0 would run MadnessFeed as root, which PUID/PGID exist to avoid. Use a non-root ID, e.g. 99/100 on unRAID or 1000/1000." >&2
        exit 1
    fi
}
check_id PUID "$PUID"
check_id PGID "$PGID"

# Switching from root to PUID:PGID needs CAP_SETUID (bit 7) and CAP_SETGID (bit 6),
# which --cap-drop=ALL removes. Fail with instructions instead of gosu's bare EPERM.
CAP_EFF="$(sed -n 's/^CapEff:[[:space:]]*//p' /proc/self/status)"
if [ -n "$CAP_EFF" ] && [ $(( 0x$CAP_EFF & 0xC0 )) -ne $(( 0xC0 )) ]; then
    cat >&2 <<EOF
entrypoint: cannot switch from root to PUID:PGID (${PUID}:${PGID}): the container is missing the
entrypoint: SETUID/SETGID capabilities (usually because of --cap-drop=ALL).
entrypoint:
entrypoint: Fix it in one of these ways:
entrypoint:  1. Recommended: remove PUID/PGID and let Docker start the container as that user,
entrypoint:     e.g. add: --user ${PUID}:${PGID}
entrypoint:     (unRAID: Edit container > Advanced View > Extra Parameters)
entrypoint:     Your data directory must already be owned by ${PUID}:${PGID} on the host.
entrypoint:  2. Keep PUID/PGID and allow only the capabilities needed to switch user:
entrypoint:     --cap-drop=ALL --cap-add=SETUID --cap-add=SETGID
EOF
    exit 1
fi

# Edit /etc/passwd and /etc/group directly instead of using usermod/groupmod: those also try to
# chown the madnessfeed user's home directory tree, which needs CAP_CHOWN. A plain text edit to
# files root already owns doesn't. PUID/PGID are validated as numbers above, so they're safe in sed.
if [ "$PGID" != "$(getent group madnessfeed | cut -d: -f3)" ]; then
    sed -i "s/^\(madnessfeed:[^:]*:\)[0-9]*:/\1${PGID}:/" /etc/group
fi
if [ "$PUID" != "$(id -u madnessfeed)" ] || [ "$PGID" != "$(id -g madnessfeed)" ]; then
    sed -i "s/^\(madnessfeed:[^:]*:\)[0-9]*:[0-9]*:/\1${PUID}:${PGID}:/" /etc/passwd
fi

# Best-effort: this is a no-op (and needs CAP_CHOWN) if the data directory
# is already owned by PUID:PGID, e.g. pre-chowned on the host.
chown -R madnessfeed:madnessfeed "$DATA_DIR" 2>/dev/null ||
    echo "entrypoint: could not chown $DATA_DIR (missing CAP_CHOWN?), continuing" >&2

exec gosu madnessfeed:madnessfeed "$@"
