#!/usr/bin/env bash

export POETRY_DOCKER_CI_MODE=1

skip_task_if_not_exists() {
  local task_name=$1
  if ./gradlew tasks --all | grep -q "$task_name"; then
    ./gradlew "$task_name"
  else
    echo "Task '$task_name' does not exist, skipping."
  fi
}

skip_task_if_not_exists pushPoetry${PYTHON_VERSION}${DEBIAN_VERSION}Image
skip_task_if_not_exists pushPlaywright${PYTHON_VERSION}${DEBIAN_VERSION}Image

#skip_task_if_not_exists pushCuda${CUDA_VERSION}Poetry${PYTHON_VERSION}${DEBIAN_VERSION}Image
#skip_task_if_not_exists pushCuda${CUDA_VERSION}Playwright${PYTHON_VERSION}${DEBIAN_VERSION}Image
