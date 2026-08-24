#!/bin/bash
cd "$(dirname "$0")"

NEEDJAVA=0
if ! command -v java >/dev/null 2>&1; then
    NEEDJAVA=1
else
    JVER=$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+)\..*/\1/')
    if ! [ "$JVER" -ge 23 ] 2>/dev/null; then
        NEEDJAVA=1
    fi
fi

if [ "$NEEDJAVA" = 1 ]; then
    echo ""
    echo "This app needs Java 23 or newer."
    echo "Download it here: https://www.oracle.com/java/technologies/downloads/"
    echo ""
    open "https://www.oracle.com/java/technologies/downloads/"
    read -p "Press Enter to exit..."
    exit 1
fi

java -jar petri-swing-ui-macos.jar
