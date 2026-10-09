#!/usr/bin/env bash
# Runs Gradle and, when it fails, repeats the important error lines as GitHub annotations,
# so the reason for a red build is visible on the pull request without opening the full log.
set -o pipefail
log=$(mktemp)
./gradlew "$@" 2>&1 | tee "$log"
status=$?
if [ $status -ne 0 ]; then
  grep -E "^e: |^w: .*error|What went wrong|^> |FAILED|Exception|error:|Unresolved|Could not" "$log" \
    | grep -v "^> Task" | head -60 \
    | while IFS= read -r line; do
        line=${line//'%'/'%25'}
        echo "::error title=Gradle::${line}"
      done
fi
exit $status
