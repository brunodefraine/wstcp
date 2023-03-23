#!/bin/bash -e

if [ $# -lt 2 ]; then
    echo "Usage: $0 <outfile> <cmd> [options...]"
    exit 1
fi

out="$1"
shift
cmd=("$1")
shift

dir=
tmp=
while [ $# -ne 0 ]; do
    cmd+=("$1")
    if [[ "$1" == "-d" ]]; then
        shift
        if [ $# -ne 0 ]; then
            dir="$1"
            if ! [ -d "$dir" ]; then
                echo "option -d does not specify a directory: $dir"
                exit 1
            fi
            tmp="$(mktemp -d -p "$dir")"
            cmd+=("$tmp")
        fi
    fi
    shift
done

if [ -z "$dir" ]; then
    echo "cmd options do not set output dir?"
    exit 1
fi

if "${cmd[@]}"; then
    find "$tmp" -type f | while read f; do
        b="${f#"$tmp"/}"
        d="${b%/*}"
        mkdir -p "$dir/$d"
        mv "$f" "$dir/$b"
        echo "$b"
    done >"$out"

    find "$tmp" -type d -delete
else
    find "$tmp" -delete
    exit 1
fi
