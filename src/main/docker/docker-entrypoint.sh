#!/bin/bash

if [ -e "/setup" ]; then
	. /setup-functions.sh
	bash /setup
fi

su - poetry-runner <<EOF
if [ -z "$APP_ROOT" ]; then
  export APP_ROOT=/app
fi
cd "$APP_ROOT"
if [ -e "./start" ]; then
	/bin/bash ./start
else
	/bin/bash /start
fi
EOF
