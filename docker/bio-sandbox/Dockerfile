# 使用官方的 Miniconda3 作为基础镜像
FROM continuumio/miniconda3:latest

# 设置工作目录
WORKDIR /workspace

# 移除默认的源，并配置清华大学 (TUNA) 镜像源以解决网络连接失败的问题
RUN conda config --remove channels defaults || true && \
    conda config --add channels https://mirrors.tuna.tsinghua.edu.cn/anaconda/pkgs/free/ && \
    conda config --add channels https://mirrors.tuna.tsinghua.edu.cn/anaconda/pkgs/main/ && \
    conda config --add channels https://mirrors.tuna.tsinghua.edu.cn/anaconda/cloud/conda-forge/ && \
    conda config --add channels https://mirrors.tuna.tsinghua.edu.cn/anaconda/cloud/bioconda/ && \
    conda config --set show_channel_urls yes

# 安装核心生信软件
RUN conda install -y fastp hisat2 subread && \
    conda clean -a -y

# 验证安装是否成功
RUN fastp --version && hisat2 --version && featureCounts -v

# 设置默认启动命令
CMD ["/bin/bash"]