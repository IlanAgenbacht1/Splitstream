#!/usr/bin/env bash
# Turns what went wrong in a Maven log into one annotation, so a failure is readable on the run's summary page
# without opening the logs.
#   .github/report-errors.sh maven.log
log="$1"
[ -f "$log" ] || exit 0
# A later step failed, not Maven
grep -q "BUILD SUCCESS" "$log" && exit 0

# native-image reports its own errors without Maven's [ERROR] prefix
native=$(grep -E -A 12 '^(Fatal error|Error: )' "$log" | grep -vE '^\s+at (java\.|jdk\.|org\.graalvm|com\.oracle\.svm)' | head -n 60)
maven=$(grep -E '^\[ERROR\]' "$log" | grep -vE 'Re-run Maven|full stack trace|Help 1|^\[ERROR\] *$|For more information|^\[ERROR\]\s+at ' | head -n 25)
errors=$(printf '%s\n%s' "$native" "$maven" | sed '/^$/d')
[ -n "$errors" ] || errors=$(tail -n 40 "$log")

# Annotations are one line; %0A is how GitHub encodes a newline inside one
echo "::error title=Maven failed::$(echo "$errors" | sed ':a;N;$!ba;s/%/%25/g;s/\r//g;s/\n/%0A/g')"
