# docker/python-sandbox/Dockerfile
FROM python:3.9-slim

# 设置环境变量
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    MPLBACKEND=Agg

# 创建目录和用户
RUN useradd -m -u 1000 sandbox && \
    mkdir -p /tmp/sandbox && \
    chown sandbox:sandbox /tmp/sandbox

USER sandbox
WORKDIR /tmp/sandbox

# 只安装必要的包
COPY requirements.txt .
RUN pip install --user --no-cache-dir -r requirements.txt

CMD ["sleep", "infinity"]