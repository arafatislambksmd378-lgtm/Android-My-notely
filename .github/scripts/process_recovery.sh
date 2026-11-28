#!/usr/bin/env bash
# Runs in CI AFTER ordinary instrumentation. Never points at a user's device.
set -Eeuo pipefail
trap 'echo "::error title=Process recovery harness::Command failed at line $LINENO (stage ${stage:-startup})"' ERR
APP=com.notely.app.debug
RUNNER=com.notely.app.debug.test/androidx.test.runner.AndroidJUnitRunner
CLASS=com.notely.app.ProcessRecoveryInstrumentedTest
emulator=$(adb shell getprop ro.boot.qemu | tr -d '\r')
legacy=$(adb shell getprop ro.kernel.qemu | tr -d '\r')
[[ "$emulator" == 1 || "$legacy" == 1 ]]
mkdir -p process-recovery-results
# AGP may uninstall instrumentation/target APKs after connected tests. Install both
# device-job APKs explicitly before the independent host-driven sessions.
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
for permission in android.permission.CAMERA android.permission.POST_NOTIFICATIONS; do
  adb shell pm grant "$APP" "$permission"
done
adb shell pm list instrumentation
adb shell run-as "$APP" pwd
stage_run() {
  local stage=$1 mode=$2
  adb shell run-as "$APP" rm -f files/recovery-e2e/ready
  adb shell am instrument -w -r -e class "$CLASS" -e recoveryStage "$stage" "$RUNNER" > "process-recovery-results/$stage.txt" 2>&1 &
  local harness=$!
  local ready=0
  for _ in $(seq 1 90); do
    marker=$(adb shell run-as "$APP" cat files/recovery-e2e/ready 2>/dev/null | tr -d '\r' || true)
    if [ "$marker" = "$stage" ]; then ready=1; break; fi
    if ! kill -0 "$harness" 2>/dev/null; then break; fi
    sleep 2
  done
  if [ "$ready" != 1 ]; then cat "process-recovery-results/$stage.txt"; echo "::error::Recovery stage $stage never acknowledged a durable snapshot"; exit 1; fi
  local before
  before=$(adb shell pidof "$APP" | tr -d '\r')
  test -n "$before"
  if [ "$mode" = force-stop ]; then
    adb shell am force-stop "$APP"
  else
    adb shell run-as "$APP" kill -9 "$before"
  fi
  wait "$harness" || true # termination is intentional, assertions above must already have passed
  # ActivityManager process-group termination can be asynchronous. Require the
  # captured PID(s) to disappear, not a zero-delay pidof immediately after Binder returns.
  local remaining="" gone=0
  for _ in $(seq 1 40); do
    remaining=$(adb shell pidof "$APP" 2>/dev/null | tr -d '\r' || true)
    local old_alive=0
    for old_pid in $before; do
      if grep -qw "$old_pid" <<< "$remaining"; then old_alive=1; fi
    done
    if [ "$old_alive" = 0 ]; then gone=1; break; fi
    sleep 0.25
  done
  if [ "$gone" != 1 ]; then
    echo "::error::Old process did not terminate: before=$before remaining=$remaining"
    exit 1
  fi
  echo "Stage $stage: durable acknowledgement then $mode, old PID $before terminated; current PID(s): ${remaining:-none}" | tee -a process-recovery-results/summary.txt
}
stage_run prepare force-stop
stage_run second SIGKILL
adb shell am instrument -w -r -e class "$CLASS" -e recoveryStage verify "$RUNNER" > process-recovery-results/verify.txt 2>&1
cat process-recovery-results/verify.txt
grep -q 'OK (1 test)' process-recovery-results/verify.txt
! grep -qE 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=' process-recovery-results/verify.txt
adb shell run-as "$APP" cat files/recovery-e2e/verified | grep -q 'Force Stop and SIGKILL recovery verified'
echo '::notice title=Process recovery::PASS: acknowledged rich document survived Force Stop, then a second edit survived SIGKILL; UI reopened and verified. No lifecycle flush before either kill.'
