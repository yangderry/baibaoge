# 国内网络构建：基础镜像走 daocloud 镜像代理，避免 docker.io 超时
FROM docker.m.daocloud.io/library/python:3.8-slim

ENV TZ=Asia/Shanghai
RUN apt-get update && apt-get install -y --no-install-recommends tzdata \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt
COPY app.py .

VOLUME ["/backup"]
EXPOSE 5000

CMD ["python", "app.py"]
