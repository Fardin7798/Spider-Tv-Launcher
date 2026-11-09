#!/usr/bin/env bash
# spider-front.sh — keep Spider on screen while this computer is attached.
#
# WHAT THIS IS NOT
#   It does not make Spider the HOME app. This firmware resolves HOME by
#   intent-filter priority: com.google.android.apps.tv.launcherx declares 2,
#   while a non-privileged app's priority is capped at 0, so HOME still opens
#   launcherx regardless of ROLE_HOME or the stored preferred activity.
#   ./spider.sh status prints that evidence; ./spider.sh verify exits 1.
#
# WHAT IT DOES
#   Every INTERVAL seconds it reads mCurrentFocus. If launcherx is on top it
#   starts com.spidertv.launcher/.SpiderHomeActivity. That is a focus bounce,
#   not ownership:
#     - needs this computer awake, on the network, with adb authorised;
#     - a TV reboot, Wi-Fi drop or PC sleep ends it immediately;
#     - each HOME press shows launcherx briefly before Spider comes back;
#     - anything you open on launcherx is taken away within INTERVAL seconds,
#       so the stock home is unusable while this runs.
#
# USAGE
#   ./spider-front.sh                 # 2 second poll
#   INTERVAL=5 ./spider-front.sh      # slower, less adb traffic
#   SERIAL=<transport> ./spider-front.sh
#   ADB=/path/to/adb ./spider-front.sh
#   Ctrl-C stops it. Nothing on the TV is changed, installed or disabled.

set -uo pipefail

SPIDER=com.spidertv.launcher
SPIDER_ACT="$SPIDER/.SpiderHomeActivity"
LAUNCHER=com.google.android.apps.tv.launcherx
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

INTERVAL="${INTERVAL:-2}"
case "$INTERVAL" in
    ''|*[!0-9.]*) INTERVAL=2 ;;
esac
if ! awk -v v="$INTERVAL" 'BEGIN{exit !(v >= 0.5)}' 2>/dev/null; then
    INTERVAL=2
fi

find_adb() {
    if [ -n "${ADB:-}" ] && command -v "$ADB" >/dev/null 2>&1; then
        printf '%s\n' "$ADB"; return 0
    fi
    if command -v adb >/dev/null 2>&1; then
        command -v adb; return 0
    fi
    local c
    for c in "$HERE/tools/platform-tools/adb.exe" "$HERE/tools/platform-tools/adb" \
             "$HERE/platform-tools/adb.exe" "$HERE/platform-tools/adb" \
             "$HERE/tools/platform-tools-r34/adb.exe" "$HERE/tools/platform-tools-r34/adb" \
             "$HERE/platform-tools-r34/adb.exe" "$HERE/platform-tools-r34/adb"; do
        [ -x "$c" ] && { printf '%s\n' "$c"; return 0; }
    done
    return 1
}
if ! ADB="$(find_adb)"; then
    echo "spider-front: no adb found. Set ADB=/path/to/adb." >&2
    exit 1
fi

pick_device() {
    "$ADB" devices 2>/dev/null | tr -d '\r' | awk 'NR>1 && $2=="device"{print $1; exit}'
}

S="$(pick_device)"
if [ -z "$S" ]; then
    echo "spider-front: no device answered. Wake the TV, check wireless debugging," >&2
    echo "              then run ./spider.sh status to confirm the transport." >&2
    exit 1
fi
if ! "$ADB" -s "$S" shell pm list packages 2>/dev/null | tr -d '\r' | grep -qx "package:$SPIDER"; then
    echo "spider-front: $SPIDER is not installed on $S. Run ./spider.sh install first." >&2
    exit 1
fi

stop() {
    printf '\nspider-front: stopped. Nothing was changed on the TV; HOME still resolves to %s.\n' "$LAUNCHER"
    exit 0
}
trap stop INT TERM

# Sleep in a child and wait on it: a signal interrupts wait cleanly instead of
# making bash print "Terminated  sleep ..." to the terminal.
pause() { sleep "$INTERVAL" & wait $!; }

printf '==> spider-front on %s\n' "$S"
printf '    %s -> %s every %ss (Ctrl-C to stop)\n' "$LAUNCHER" "$SPIDER" "$INTERVAL"
printf '    Needs this computer awake and connected; HOME still resolves to %s.\n\n' "$LAUNCHER"

misses=0
while :; do
    s="${SERIAL:-$(pick_device)}"
    if [ -z "$s" ]; then
        misses=$((misses + 1))
        if [ "$misses" -eq 1 ]; then
            echo "waiting for a device — is the TV awake and wireless debugging on?" >&2
        fi
        pause
        continue
    fi
    misses=0

    focus="$("$ADB" -s "$s" shell dumpsys window 2>/dev/null | tr -d '\r' | grep -m1 mCurrentFocus)"
    case "$focus" in
        *"$LAUNCHER"*)
            printf '%s launcherx focused -> Spider\n' "$(date +%H:%M:%S)"
            "$ADB" -s "$s" shell am start -n "$SPIDER_ACT" >/dev/null 2>&1 ||
                echo "  am start failed; is $SPIDER still installed?" >&2
            ;;
    esac
    pause
done
