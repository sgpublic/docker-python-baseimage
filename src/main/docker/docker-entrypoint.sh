#!/bin/bash

if [ -e "./setup" ]; then
  . /setup-functions.sh
	bash ./setup
elif [ -e "/setup" ]; then
  . /setup-functions.sh
	bash /setup
fi

cd /app
if [ -e "./start" ]; then
  sudo -u poetry-runner bash -c ./start
else
  sudo -u poetry-runner bash -c /start
fi
