#!/usr/bin/env bash
# Spider TV Launcher — guarded install for the Family room TV.
#
# WHY THIS EXISTS INSTEAD OF UPSTREAM tools/apply.sh
# -------------------------------------------------
# Upstream Phase 3 runs `pm disable-user` on whatever launcher currently owns
# HOME. On this TV that is com.google.android.apps.tv.launcherx, and disabling
# it leaves ROLE_HOME unresolvable: HOME falls through to setupwraith's
# RecoveryActivity, which re-fires HOME hundreds of times per press, the UI
# flickers black and adbd dies with it. See DEBLOAT.md, "launcher experiment".
#
# tools/protected.txt does not list launcherx, and KNOWN_LAUNCHERS only guards
# Phase 4's generic debloat loop — not Phase 3. So this script never calls
# apply.sh at all: install, verify, switch HOME, and leave launcherx ENABLED as
# a fallback so a crash in Spider lands on a working home, not an INPUT storm.
#
# Escape hatch, any time:  ./tv.sh home
#
# USAGE
#   ./spider.sh patch     clone repo, apply the launcherx guard, run guard test
#   ./spider.sh install   install APK + make it HOME (launcherx untouched)
#   ./spider.sh status    who owns HOME now? is launcherx enabled?
#   ./spider.sh verify    press HOME, confirm it lands on Spider (no install)
#   ./spider.sh revert    hand HOME back to Google's launcher
#
# Nothing here disables a package. There is deliberately no `disable` verb.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_URL="https://github.com/Fardin7798/Spider-Tv-Launcher.git"
# Where the clone lives: beside this script (the usual layout) or above it when
# the script is committed inside the repo itself as tools/spider.sh.
if [ -d "$SCRIPT_DIR/.git" ]; then
    SRC="$SCRIPT_DIR"
elif [ -d "$SCRIPT_DIR/../.git" ] && [ -f "$SCRIPT_DIR/../tools/apply.sh" ]; then
    SRC="$(cd "$SCRIPT_DIR/.." && pwd)"
else
    SRC="$SCRIPT_DIR/Spider-Tv-Launcher"
fi
APK="$SRC/app/build/outputs/apk/release/app-release.apk"
APK_URL="https://github.com/Fardin7798/Spider-Tv-Launcher/releases/latest/download/app-release.apk"
LAUNCHER="com.google.android.apps.tv.launcherx"
SPIDER="com.spidertv.launcher"
SPIDER_HOME="$SPIDER/.SpiderHomeActivity"
HOME_QUERY="cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# Keep the caller's ADB before clearing the variable: the loop below used to
# read "${ADB:-}" after ADB was already emptied, so the override never applied.
ENV_ADB="${ADB:-}"
ADB=""
for c in "$ENV_ADB" "$SCRIPT_DIR/../tools/platform-tools/adb.exe" \
         "$SCRIPT_DIR/tools/platform-tools/adb.exe" "$(command -v adb || true)"; do
    [ -n "$c" ] && [ -x "$c" ] && { ADB="$c"; break; }
done
[ -n "$ADB" ] || die "adb not found (set ADB=/path/to/adb)"

need_device() {
    local s
    s="$("$ADB" devices 2>/dev/null | tr -d '\r' | awk 'NR>1 && $2=="device"{print $1; exit}')"
    if [ -z "$s" ]; then
        s="$("$ADB" mdns services 2>/dev/null | tr -d '\r' \
             | awk '$2=="_adb-tls-connect._tcp"{print $3}' | tail -1)"
        if [ -n "$s" ]; then
            echo "    reconnecting to $s" >&2
            "$ADB" connect "$s" >/dev/null 2>&1; sleep 1
            s="$("$ADB" devices 2>/dev/null | tr -d '\r' | awk 'NR>1 && $2=="device"{print $1; exit}')"
        fi
    fi
    [ -n "$s" ] || die "no TV connected (wake the TV, or ./tv.sh connect)"
    printf '%s\n' "$s"
}

home_owner() { "$ADB" -s "$1" shell $HOME_QUERY 2>/dev/null | tr -d '\r' | tail -1 | cut -d/ -f1; }

# What the HOME key actually opens. The resolver and the HOME role can both name
# one package while the screen shows another, so this reads the focused window —
# the only honest proof of who owns HOME on this build.
home_focus() {
    "$ADB" -s "$1" shell dumpsys window 2>/dev/null | tr -d '\r' \
        | grep -m1 'mCurrentFocus' \
        | grep -o '[A-Za-z0-9._]*/[A-Za-z0-9._$]*' | head -1
}

# Intent-filter priority a package declares for HOME. Android 14 decides the
# HOME dispatch on this before it reads any role or preferred-activity record,
# which is why install compares it instead of trusting set-home-activity.
home_priority() {
    "$ADB" -s "$1" shell cmd package query-activities --brief \
        -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null \
        | tr -d '\r' \
        | awk -v want="$2" '
            /priority=/ { p = $1; sub(/^priority=/, "", p) }
            {
                line = $0
                gsub(/^[ \t]+|[ \t]+$/, "", line)
                if (index(line, "/") > 0) {
                    split(line, c, "/")
                    if (c[1] == want) { print p; exit }
                }
            }'
}

is_num() { case "$1" in ''|*[!0-9-]*) return 1 ;; esac; return 0; }

# Package state from an already-fetched `pm list packages` dump, so status and
# install never assume a failed query means "not installed".
pkg_state() {  # $1 = all packages, $2 = disabled packages, $3 = package
    printf '%s\n' "$1" | grep -qxF "package:$3" || { printf 'not installed'; return 0; }
    if printf '%s\n' "$2" | grep -qxF "package:$3"; then
        printf 'installed, DISABLED'
    else
        printf 'installed, enabled'
    fi
}

pkg_installed() {  # $1 = device, $2 = package
    "$ADB" -s "$1" shell pm list packages 2>/dev/null | tr -d '\r' | grep -qxF "package:$2"
}

pkg_disabled() {  # $1 = device, $2 = package
    "$ADB" -s "$1" shell pm list packages -d 2>/dev/null | tr -d '\r' | grep -qxF "package:$2"
}

# launcherx's own HOME activity. A bare HOME query answers with whoever owns
# HOME now (Spider, after install), so ask for every HOME activity and pick
# launcherx's; `resolve-activity` scoped with -p is the second opinion, and
# .HomeActivity is the last resort.
launcherx_home() {
    local s="$1" c
    c="$("$ADB" -s "$s" shell cmd package query-activities --brief \
            -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null \
         | tr -d '\r' | grep -o "$LAUNCHER/[^[:space:]]*" | awk 'NR==1')"
    [ -n "$c" ] || c="$("$ADB" -s "$s" shell cmd package resolve-activity --brief \
            -a android.intent.action.MAIN -c android.intent.category.HOME -p "$LAUNCHER" 2>/dev/null \
         | tr -d '\r' | grep -o "$LAUNCHER/[^[:space:]]*" | awk 'NR==1')"
    # Last resort: the activity the Family room TV (AI PONT, Android 14) reports.
    [ -n "$c" ] || c="$LAUNCHER/.home.HomeActivity"
    printf '%s\n' "$c"
}

# ── patch: the launcherx guard, applied idempotently ───────────────────────
cmd_patch() {
    command -v git >/dev/null 2>&1 || die "git not found"
    if [ ! -d "$SRC/.git" ]; then
        echo "==> cloning $REPO_URL"
        git clone --depth 1 "$REPO_URL" "$SRC" || die "clone failed"
    else
        echo "==> repo already present: $SRC"
    fi

    # 1. protected.txt — explicit, so any is_protected caller refuses it
    if grep -qx "$LAUNCHER" "$SRC/tools/protected.txt"; then
        echo "    protected.txt : already lists $LAUNCHER"
    else
        {
            printf '\n# Stock launchers must never be disabled. With ROLE_HOME\n'
            printf '# unresolvable, HOME can fall through to a setup/recovery\n'
            printf '# activity and storm the input queue.\n'
            printf '%s\n' "$LAUNCHER"
        } >> "$SRC/tools/protected.txt"
        echo "    protected.txt : added $LAUNCHER"
    fi

    # 2. apply.sh — hard stop independent of protected.txt
    if grep -q 'is_stock_launcher()' "$SRC/tools/apply.sh"; then
        echo "    apply.sh      : guard already present"
    else
        tmpb="$(mktemp)"
        cat > "$tmpb" <<'BLOCK'

# Hard stop, independent of protected.txt: stock launchers are never disabled.
# Losing ROLE_HOME is not a mistake you can undo with the remote.
STOCK_LAUNCHERS="com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher com.mitv.tvhome"
is_stock_launcher() {
    local p="$1"
    for sl in $STOCK_LAUNCHERS; do
        [ "$p" = "$sl" ] && return 0
    done
    return 1
}
BLOCK
        awk 'FNR==NR { b = b $0 ORS; next }
             { print }
             /dumpsys tv_input/ && !d { printf "%s", b; d = 1 }' \
            "$tmpb" "$SRC/tools/apply.sh" > "$SRC/tools/apply.sh.tmp" \
            || die "awk insert failed"
        mv "$SRC/tools/apply.sh.tmp" "$SRC/tools/apply.sh"
        rm -f "$tmpb"
        echo "    apply.sh      : inserted is_stock_launcher()"
    fi

    # 3. Phase 3 — the one line that disables the previous home
    if grep -q 'is_protected "\$ORIG_HOME"; then' "$SRC/tools/apply.sh"; then
        sed -i 's#if is_protected "\$ORIG_HOME"; then#if is_protected "\$ORIG_HOME" || is_stock_launcher "\$ORIG_HOME"; then#' \
            "$SRC/tools/apply.sh" || die "sed failed"
        echo "    apply.sh      : Phase 3 now refuses stock launchers"
    else
        echo "    apply.sh      : Phase 3 already guarded"
    fi

    # 4. verify — syntax, then the guard logic itself (no adb, no TV)
    bash -n "$SRC/tools/apply.sh" || die "apply.sh has a syntax error"
    echo "PASS  bash -n tools/apply.sh"

    eval "$(sed -n '/^STOCK_LAUNCHERS=/,/^}/p' "$SRC/tools/apply.sh")"
    is_stock_launcher "$LAUNCHER" \
        || die "guard NOT active for $LAUNCHER"
    echo "PASS  $LAUNCHER is refused"
    if is_stock_launcher com.android.vending; then
        die "guard is over-broad: com.android.vending refused"
    fi
    echo "PASS  ordinary packages still allowed"
    grep -q 'is_stock_launcher "\$ORIG_HOME"' "$SRC/tools/apply.sh" \
        || die "Phase 3 not guarded"
    echo "PASS  Phase 3 guarded"

    echo
    echo "==> diff"
    ( cd "$SRC" && git --no-pager diff --stat && git --no-pager diff )
}

# Shared HOME check: press HOME, then read the focused window. Prints the four
# signals and succeeds only when the HOME key actually lands on Spider. Used by
# install (after the switch) and by verify (on its own, touching nothing else).
verify_home() {  # $1 = device
    local s="$1"
    local now role focus fpkg prio_x prio_s
    now="$(home_owner "$s")"
    role="$("$ADB" -s "$s" shell cmd role get-role-holders android.app.role.HOME 2>/dev/null \
            | tr -d '\r' | awk 'NF{printf "%s%s", sep, $0; sep=", "}')"
    prio_x="$(home_priority "$s" "$LAUNCHER")"
    prio_s="$(home_priority "$s" "$SPIDER")"

    echo "==> verifying HOME (press HOME, then read the focused window)"
    "$ADB" -s "$s" shell input keyevent 3 >/dev/null 2>&1
    sleep 2
    focus="$(home_focus "$s")"
    fpkg="${focus%%/*}"

    printf '    resolved    : %s\n' "${now:-nothing}"
    printf '    role holder : %s\n' "${role:-none}"
    printf '    focused     : %s\n' "${focus:-unknown}"
    printf '    priority    : %s=%s  %s=%s\n' \
        "$LAUNCHER" "${prio_x:-?}" "$SPIDER" "${prio_s:-?}"

    if [ "$fpkg" = "$SPIDER" ]; then
        echo "    PASS  HOME opens $SPIDER"
        return 0
    fi

    echo
    echo "FAIL  HOME opens ${fpkg:-something else}, not $SPIDER."
    if [ "$now" = "$SPIDER" ] || printf '%s\n' "$role" | grep -qF "$SPIDER"; then
        echo "      The resolver and/or the HOME role name Spider, yet the HOME key"
        echo "      disagrees. On this build HOME is decided by intent-filter priority"
        echo "      before any role or preferred-activity record is consulted."
    fi
    if is_num "$prio_x" && is_num "$prio_s" && [ "$prio_s" -le "$prio_x" ]; then
        echo "      $LAUNCHER declares priority=$prio_x for HOME; $SPIDER declares"
        echo "      priority=$prio_s. The HOME role, set-home-activity and stored"
        echo "      preferred activities cannot outrank a higher-priority filter, and a"
        echo "      non-system app cannot raise its priority above the stock value — so"
        echo "      this TV cannot hand HOME to Spider while launcherx is installed and"
        echo "      enabled. Disabling launcherx drops HOME to setupwraith instead."
    fi
    return 1
}

# ── install: APK + HOME switch, launcherx left enabled ─────────────────────
cmd_install() {
    [ -d "$SRC/.git" ] || die "repo missing — run ./spider.sh patch first"
    local s; s="$(need_device)"
    echo "==> target: $s"

    local orig; orig="$(home_owner "$s")"
    echo "    home before : ${orig:-unknown}"
    "$ADB" -s "$s" shell pm list packages 2>/dev/null | tr -d '\r' \
        | grep -qx "package:$LAUNCHER" \
        && echo "    fallback    : $LAUNCHER present (will stay enabled)" \
        || echo "    WARNING     : $LAUNCHER not installed — no fallback home!"

    mkdir -p "$(dirname "$APK")"
    if [ ! -s "$APK" ]; then
        echo "==> fetching prebuilt APK (no Gradle/SDK needed)"
        curl -sSL -f -o "$APK" "$APK_URL" || die "APK download failed — TV untouched"
    fi
    echo "    apk         : $(wc -c < "$APK") bytes"

    echo "==> installing"
    local out
    out="$("$ADB" -s "$s" install -r "$APK" 2>&1 | tr -d '\r')"
    printf '    %s\n' "$out"
    case "$out" in
        *Success*) ;;
        *) die "install failed — TV untouched beyond the failed install" ;;
    esac

    echo "==> startup test before touching HOME"
    local start
    start="$("$ADB" -s "$s" shell am start -W -n "$SPIDER_HOME" 2>&1 | tr -d '\r')"
    printf '%s\n' "$start" | grep -qF 'Status: ok' \
        || die "launcher failed to start — HOME NOT changed"
    echo "    PASS  launcher runs"

    "$ADB" -s "$s" shell appops set "$SPIDER" GET_USAGE_STATS allow >/dev/null 2>&1
    echo "    PASS  GET_USAGE_STATS granted (Recents switcher)"

    echo "==> switching HOME (launcherx deliberately NOT disabled)"
    "$ADB" -s "$s" shell cmd package set-home-activity --user 0 "$SPIDER_HOME" >/dev/null 2>&1

    local failed=0
    verify_home "$s" || failed=1

    # The fallback only exists if launcherx is still enabled. If an earlier
    # apply.sh run left it disabled, put it back: a home you cannot fall back
    # to is the failure this script was written to avoid.
    if "$ADB" -s "$s" shell pm list packages -d 2>/dev/null | tr -d '\r' | grep -q "$LAUNCHER"; then
        echo
        echo "WARNING $LAUNCHER is DISABLED — Spider has no fallback home."
        echo "        Re-enabling it (this script never leaves a package off)."
        "$ADB" -s "$s" shell pm enable "$LAUNCHER" >/dev/null 2>&1
        if "$ADB" -s "$s" shell pm list packages -d 2>/dev/null | tr -d '\r' | grep -q "$LAUNCHER"; then
            echo "FAIL  pm enable did not take. From a host shell:"
            echo "      $ADB -s $s shell pm enable $LAUNCHER"
            return 1
        fi
        echo "    PASS  $LAUNCHER re-enabled"
    else
        echo "    PASS  $LAUNCHER still enabled (fallback intact)"
    fi

    if [ "$failed" -ne 0 ]; then
        echo
        echo "      Nothing was disabled: Spider is installed and launchable, and the"
        echo "      stock launcher still owns HOME. Check with: ./spider.sh status"
        echo "      Hand HOME back explicitly with: ./spider.sh revert"
        return 1
    fi

    echo
    echo "==> done"
    echo "    home     : $SPIDER  ($SPIDER_HOME)"
    echo "    fallback : $LAUNCHER enabled"
    echo "    status   : ./spider.sh status"
    echo "    revert   : ./spider.sh revert   (or ./tv.sh home)"
}

# ── status: read-only report, same evidence install checks ────────────────
cmd_status() {
    local s; s="$(need_device)"
    local all dis now role opts focus fpkg prio_x prio_s lx_disabled
    all="$("$ADB" -s "$s" shell pm list packages 2>/dev/null | tr -d '\r')"
    dis="$("$ADB" -s "$s" shell pm list packages -d 2>/dev/null | tr -d '\r')"
    [ -n "$all" ] || die "could not read the package list from $s"

    now="$(home_owner "$s")"
    role="$("$ADB" -s "$s" shell cmd role get-role-holders android.app.role.HOME 2>/dev/null \
            | tr -d '\r' | awk 'NF{printf "%s%s", sep, $0; sep=", "}')"
    opts="$("$ADB" -s "$s" shell appops get "$SPIDER" GET_USAGE_STATS 2>/dev/null \
            | tr -d '\r' | awk 'NF{print; exit}')"
    focus="$(home_focus "$s")"
    fpkg="${focus%%/*}"
    prio_x="$(home_priority "$s" "$LAUNCHER")"
    prio_s="$(home_priority "$s" "$SPIDER")"

    printf '%s\n' "$dis" | grep -qxF "package:$LAUNCHER" && lx_disabled=1 || lx_disabled=0

    echo "==> target: $s"
    printf '    focused     : %s\n' "${focus:-unknown}"
    printf '    resolved    : %s\n' "${now:-nothing resolved}"
    printf '    role holder : %s\n' "${role:-none}"
    printf '    priority    : %s=%s  %s=%s\n' \
        "$LAUNCHER" "${prio_x:-?}" "$SPIDER" "${prio_s:-?}"
    printf '    spider      : %s\n' "$(pkg_state "$all" "$dis" "$SPIDER")"
    printf '    usage stats : %s\n' "${opts:-not set}"
    printf '    launcherx   : %s\n' "$(pkg_state "$all" "$dis" "$LAUNCHER")"
    echo

    if [ "$lx_disabled" -eq 1 ]; then
        echo "WARN  $LAUNCHER is DISABLED — Spider has no fallback home."
        echo "      A crash in Spider lands on setupwraith/.RecoveryActivity."
        echo "      Fix: pm enable $LAUNCHER   (or: ./spider.sh revert)"
        echo
    fi

    if [ "$now" = "$SPIDER" ] && [ "$fpkg" = "$SPIDER" ]; then
        echo "OK    Spider owns HOME and is the focused app."
    elif [ "$now" = "$SPIDER" ]; then
        echo "OK    Spider owns HOME; focused app is ${fpkg:-nothing}."
        echo "      Press HOME on the TV to confirm it lands on Spider."
    elif [ "$now" = "$LAUNCHER" ] && printf '%s\n' "$role" | grep -qF "$SPIDER"; then
        echo "WARN  Mismatch: HOME resolves to $LAUNCHER while Spider holds the role."
        echo "      The resolver, the role and the screen disagree — the tell for this"
        echo "      build: HOME is decided by intent-filter priority before any role or"
        echo "      preferred-activity record is consulted."
        if is_num "$prio_x" && is_num "$prio_s"; then
            echo "      priorities: $LAUNCHER=$prio_x  $SPIDER=$prio_s"
            if [ "$prio_s" -le "$prio_x" ]; then
                echo "      Spider cannot outrank launcherx while it is installed and enabled;"
                echo "      disabling launcherx drops HOME to setupwraith instead."
            fi
        fi
    elif [ "$now" = "$LAUNCHER" ]; then
        echo "OK    Google's launcherx owns HOME (Spider not the active home)."
    else
        echo "NOTE  HOME resolves to ${now:-nothing}; neither Spider nor launcherx."
        echo "      If that is a setup/recovery activity, run: ./spider.sh revert"
    fi
}

# ── revert: hand HOME back to launcherx, leave Spider installed ────────────
cmd_revert() {
    local s; s="$(need_device)"
    echo "==> target: $s"

    pkg_installed "$s" "$LAUNCHER" \
        || die "$LAUNCHER is not installed on $s — nothing to revert to"
    if pkg_disabled "$s" "$LAUNCHER"; then
        echo "    launcherx   : DISABLED — re-enabling so it can take HOME back"
        "$ADB" -s "$s" shell pm enable "$LAUNCHER" >/dev/null 2>&1
        pkg_disabled "$s" "$LAUNCHER" \
            && die "could not re-enable $LAUNCHER (try: $ADB -s $s shell pm enable $LAUNCHER)"
        echo "    PASS        $LAUNCHER enabled"
    fi

    local home; home="$(launcherx_home "$s")"
    local orig; orig="$(home_owner "$s")"
    echo "    home before : ${orig:-unknown}"
    echo "==> setting HOME to $home"
    "$ADB" -s "$s" shell cmd package set-home-activity --user 0 "$home" >/dev/null 2>&1

    local now; now="$(home_owner "$s")"
    printf '    home after  : %s\n' "${now:-nothing}"
    if [ "$now" != "$LAUNCHER" ]; then
        echo
        echo "FAIL  HOME did not return to $LAUNCHER."
        echo "      Try: $ADB -s $s shell cmd package set-home-activity --user 0 $home"
        echo "      Or on the TV: Settings > Apps > Default apps > Home app"
        return 1
    fi

    echo
    echo "==> done — Google's launcher owns HOME again"
    echo "    Spider stays installed (remove with: pm uninstall $SPIDER)"
}

# ── verify: the HOME check alone, nothing installed or switched ────────────
cmd_verify() {
    local s; s="$(need_device)"
    local all
    all="$("$ADB" -s "$s" shell pm list packages 2>/dev/null | tr -d '\r')"
    [ -n "$all" ] || die "could not read the package list from $s"
    echo "==> target: $s"
    if ! printf '%s\n' "$all" | grep -qxF "package:$SPIDER"; then
        echo "FAIL  $SPIDER is not installed on $s."
        echo "      Run ./spider.sh install first."
        return 1
    fi
    if verify_home "$s"; then
        return 0
    fi
    echo
    echo "      Spider is installed but does not own HOME. Full picture:"
    echo "      ./spider.sh status"
    return 1
}

usage() {
    cat <<'USAGE'
Spider TV Launcher — guarded install for the Family room TV.

  ./spider.sh patch     clone repo, apply the launcherx guard, run guard test
  ./spider.sh install   install APK + make it HOME (launcherx untouched)
  ./spider.sh verify    press HOME, confirm it lands on Spider (no install)
  ./spider.sh status    who owns HOME now? is launcherx enabled?
  ./spider.sh revert    hand HOME back to Google's launcher

Nothing here disables a package. There is deliberately no `disable` verb.
Set ADB=/path/to/adb if adb is not on PATH or in tools/platform-tools.
USAGE
}

case "${1:-}" in
    patch|install|status|revert|verify) "cmd_$1" ;;
    ""|-h|--help|help)           usage ;;
    disable|disable-user|uninstall)
        printf 'ERROR: there is no %s verb on purpose — this script never disables a package.\n' "$1" >&2
        printf '       To change HOME back, run: ./spider.sh revert\n' >&2
        exit 2 ;;
    *)
        printf 'ERROR: unknown verb: %s\n\n' "$1" >&2
        usage >&2
        exit 2 ;;
esac
