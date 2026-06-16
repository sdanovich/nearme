#!/usr/bin/env bash
#
# NearMe — Cloudflare quick-tunnel runner with URL auto-publish.
#
# Exposes the local backend through an (ephemeral) Cloudflare quick tunnel and
# publishes the public URL to the `tunnel-url` branch as `url.txt`, so the Android
# app can re-discover it after the hostname rotates (see BaseUrlProvider and
# TunnelRecoveryInterceptor). No open ports / router changes required.
#
# The branch holds a single file and is force-pushed, so it never touches `main`'s
# history. Requires push access to origin (SSH key) and ~/.local/bin/cloudflared.
#
# Run:  deploy/tunnel.sh          (Ctrl-C to stop)
# Env overrides: NEARME_BACKEND_URL, NEARME_TUNNEL_BRANCH, CLOUDFLARED, NEARME_TUNNEL_LOCK
set -uo pipefail

# Single-instance guard. The @reboot crontab entry, the */5 watchdog, and any
# manual run all invoke this script; without a lock two supervisors could run at
# once, each spawning its own cloudflared and racing to publish a different URL to
# the anchor branch (URL flapping, app can't settle). Hold an exclusive lock for
# the whole process lifetime; a duplicate launch fails the lock and exits cleanly.
LOCK="${NEARME_TUNNEL_LOCK:-/tmp/nearme-tunnel.lock}"
exec 9>"$LOCK" || { echo "[tunnel] cannot open lock $LOCK"; exit 1; }
if ! flock -n 9; then
    echo "[tunnel] another instance already running (lock $LOCK held); exiting"
    exit 0
fi

BACKEND_URL="${NEARME_BACKEND_URL:-http://localhost:28585}"
BRANCH="${NEARME_TUNNEL_BRANCH:-tunnel-url}"
CF="${CLOUDFLARED:-$HOME/.local/bin/cloudflared}"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REMOTE="$(git -C "$REPO_DIR" remote get-url origin)"

# Publish $1 as the sole content of url.txt on $BRANCH (orphan, force-pushed).
publish() {
    local url="$1" tmp
    tmp="$(mktemp -d)"
    git clone --quiet --depth 1 "$REMOTE" "$tmp" || { rm -rf "$tmp"; return 1; }
    git -C "$tmp" checkout --quiet --orphan "$BRANCH"
    git -C "$tmp" rm -rfq . 2>/dev/null || true
    printf '%s\n' "$url" > "$tmp/url.txt"
    git -C "$tmp" add url.txt
    git -C "$tmp" -c user.name='nearme-tunnel' -c user.email='tunnel@nearme.local' \
        commit --quiet -m "tunnel url: $url"
    git -C "$tmp" push --quiet --force origin "HEAD:$BRANCH"
    local rc=$?
    rm -rf "$tmp"
    [ $rc -eq 0 ] && echo "[tunnel] published $url -> $BRANCH/url.txt"
    return $rc
}

echo "[tunnel] backend=$BACKEND_URL branch=$BRANCH remote=$REMOTE"
last=""
while true; do
    # Stream cloudflared output; publish each new tunnel URL we see.
    while IFS= read -r line; do
        if [[ "$line" =~ (https://[a-z0-9-]+\.trycloudflare\.com) ]]; then
            url="${BASH_REMATCH[1]}"
            if [ "$url" != "$last" ]; then
                last="$url"
                publish "$url" || echo "[tunnel] publish failed for $url"
            fi
        fi
    done < <("$CF" tunnel --url "$BACKEND_URL" --no-autoupdate 2>&1)
    echo "[tunnel] cloudflared exited; restarting in 3s..."
    sleep 3
done
