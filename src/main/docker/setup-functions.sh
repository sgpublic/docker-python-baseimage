#!/bin/bash

_APT_UPDATED=0

apt_install() {
	PACKAGE=$1
	installed_count=`apt list --installed $PACKAGE 2>/dev/null | wc -l`
	if [ $installed_count -le 1 ]; then
		if [ $_APT_UPDATED != 1 ]; then
			_APT_UPDATED=1
			apt-get update
		fi
		apt-get install $PACKAGE -y
	fi
}
