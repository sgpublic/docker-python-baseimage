FROM debian:bookworm-20230904-slim

RUN sed -i 's/deb.debian.org/mirrors.aliyun.com/' /etc/apt/sources.list.d/*.sources &&\
 apt-get update &&\
 apt-get install python3-pip python3-poetry git -y &&\
 pip config set --global global.index-url https://mirrors.aliyun.com/pypi/simple/ &&\
 git config --global --add safe.directory /app &&\
 useradd -m -u 1000 poetry-runner

USER poetry-runner

WORKDIR /app

ENTRYPOINT ["./start"]
