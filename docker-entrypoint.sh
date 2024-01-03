#!/bin/bash

if [ -e "/setup" ]; then
	. /setup-functions.sh
	bash /setup
fi

su - poetry-runner <<EOF
cd /app
if [ -e "./start" ]; then
	/bin/bash ./start
else
	/bin/bash /start
fi
EOF
