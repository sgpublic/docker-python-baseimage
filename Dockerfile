FROM debian:bookworm-20230904-slim

RUN sed -i 's/deb.debian.org/mirrors.aliyun.com/' /etc/apt/sources.list.d/*.sources &&\
 apt-get update &&\
 apt-get install python3-pip python3-poetry git libnss3 libnspr4 libdrm2 libgbm1 libasound2 -y &&\
 pip config set --global global.index-url https://mirrors.aliyun.com/pypi/simple/ &&\
 git config --global --add safe.directory /app &&\
 useradd -m -u 1000 poetry-runner &&\
 mkdir -p /home/poetry-runner/.cache &&\
 chown -R poetry-runner:poetry-runner /home/poetry-runner/.cache

USER poetry-runner

WORKDIR /app

VOLUME /home/poetry-runner/.cache

ENTRYPOINT ["./start"]
