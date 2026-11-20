#!/usr/bin/env bash
# Push a large file to a device over a slow or flaky adb link, in checksummed chunks that resume
# after a drop, then reassemble and verify it on the device.
#   tools/push_resumable.sh <local file> <device path> [chunk MB]
# Uses $ADB (default: adb) and whatever ADB_SERVER_SOCKET / ANDROID_SERIAL are set.
set -u
src=$1; dst=$2; mb=${3:-2}
adb=${ADB:-adb}
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
split -b ${mb}M -d -a 3 "$src" "$work/part."
rdir="$dst.parts"
$adb shell mkdir -p "$rdir" || exit 1
for f in "$work"/part.*; do
  name=$(basename "$f"); want=$(md5sum "$f" | cut -d' ' -f1)
  for try in 1 2 3 4 5; do
    have=$($adb shell md5sum "$rdir/$name" 2>/dev/null | cut -d' ' -f1)
    [ "$have" = "$want" ] && break
    echo "$(date +%T) push $name (try $try)"
    $adb push "$f" "$rdir/$name" >/dev/null 2>&1 || sleep 5
  done
  [ "$have" = "$want" ] || { echo "FAILED $name"; exit 1; }
done
$adb shell "cat $rdir/part.* > $dst && rm -r $rdir" || exit 1
[ "$($adb shell md5sum "$dst" | cut -d' ' -f1)" = "$(md5sum "$src" | cut -d' ' -f1)" ] && echo "OK $dst" || { echo "CHECKSUM MISMATCH"; exit 1; }
