#!/bin/sh
# Host fixture for numeric inode owners without privileged chown.
# Usage: fake-ownership.sh chown OWNER PATH... | fake-ownership.sh ls ARGS...
# FAKE_OWNERS holds "inode uid gid" lines; the last line for an inode wins.
# Shell + awk on purpose: the runtime calls ls for every size and metadata read,
# and one interpreter start per call dominated the suites that use this fixture.
store=${FAKE_OWNERS:?}
mode=$1
shift
if [ "$mode" = chown ]; then
    owner=$1
    shift
    uid=${owner%%[.:]*}
    gid=${owner#*[.:]}
    for path do
        inode=$(/bin/ls -diL "$path" | awk '{ print $1 }') && [ -n "$inode" ] || exit 1
        echo "$inode $uid $gid" >> "$store"
    done
    exit 0
fi
# ls -i prefixes each entry with its inode; long-format entries get the recorded
# owner (simulated root by default), the prefix is dropped from every line.
listing=$(/bin/ls -i "$@")
status=$?
[ -z "$listing" ] || echo "$listing" | awk -v store="$store" '
BEGIN {
    while ((getline line < store) > 0) {
        split(line, field, " ")
        owner[field[1]] = field[2] " " field[3]
    }
}
$1 ~ /^[0-9]+$/ && NF >= 2 {
    inode = $1
    sub(/^ *[0-9]+ /, "")
    if (NF >= 9 && substr($1, 1, 1) ~ /[-dl]/) {
        split(inode in owner ? owner[inode] : "0 0", id, " ")
        $3 = id[1]
        $4 = id[2]
    }
}
{ print }
'
exit "$status"
