# poetry-docker

此镜像是为 NoneBot 应用构建的基础镜像，但适用于所有 Python 应用。

## 镜像特性

包含依赖：

+ python3-pip
+ python3-venv
+ git
+ sudo
+ ffmpeg
+ curl
+ libfreetype6-dev
+ python3-tk
+ android-sdk-platform-tools-common
+ poetry（安装目录：`/opt/poetry`，缓存目录：`/home/poetry-runner/.cache/poetry`）
+ adb（安装目录：`/opt/adb`，已添加环境变量，可直接调用）
+ rustup（安装目录：`/opt/rustup`，已添加环境变量，可直接调用）

镜像启动流程：

+ 容器内工作目录为 `/app`
+ 以 `root:root` 身份执行初始化脚本 `setup.sh`，寻找优先级：`/setup.sh` -> `/app/setup.sh`，若未寻找到则跳过。
+ 以 `poetry-runner:poetry-runner` 身份执行启动脚本 `start.sh`，寻找优先级：`/start.sh` -> `/app/start.sh`，若未寻找到则报错，无法启动镜像。

## 食用方法

选择一个你喜欢的目录，例如 `~/nonebot-app` 作为工作目录。

将 NoneBot 应用项目克隆至 `./app`，即 `~/nonebot-app/app`，创建一个启动脚本 `./start.sh`，例如：

```shell
#!/bin/bash -e

timeout 1m git pull
poetry install
poetry run nb run
```

或不使用 poetry 的启动脚本：

```shell
#!/bin/bash -e

set -v

if [ ! -d ".venv" ]; then
  python3 -m venv ./.venv
fi

source ./.venv/bin/activate

pip config set global.index-url https://mirrors.aliyun.com/pypi/simple/

timeout 1m git pull
pip install nonebot wheel
pip install -r requirements.txt

python3 run.py
```

然后创建 `./docker-compose.yaml`，例如：

```yaml
version: '3'
services:
  nonebot:
    image: mhmzx/poetry-runner:3.9-bullseye
    volumes:
      - /etc/localtime:/etc/localtime:ro
      - ./.cache:/home/poetry-runner/.cache
      - ./app:/app
      - ./start.sh:/start.sh
    restart: unless-stopped
    environment:
      TZ: Asia/Shanghai
      LANG: zh_CN.UTF8
```
