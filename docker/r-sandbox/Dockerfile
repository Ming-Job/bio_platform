# docker/r-sandbox/Dockerfile

# 使用官方R镜像
FROM rocker/r-base:latest

# 设置环境变量
ENV R_TMPDIR=/tmp/r_temp \
    TMPDIR=/tmp/r_temp \
    R_HISTFILE=/dev/null \
    R_ENVIRON_USER=/dev/null \
    R_PROFILE_USER=/dev/null \
    R_ENABLE_JIT=0

# 安装系统依赖（不更换 apt 源，使用默认）
RUN apt-get update && apt-get install -y --no-install-recommends \
    libcurl4-openssl-dev \
    libssl-dev \
    libxml2-dev \
    libpng-dev \
    libfreetype6-dev \
    && rm -rf /var/lib/apt/lists/*

# 设置 R 包镜像源为阿里云镜像
RUN echo 'options(repos = c(CRAN = "https://mirrors.aliyun.com/CRAN/"))' >> /etc/R/Rprofile.site

# 安装R包（使用阿里云镜像源）
RUN R -e "install.packages(c('ggplot2', 'gridExtra', 'jsonlite'))"

# 创建目录
RUN mkdir -p /tmp/sandbox

CMD ["sleep", "infinity"]