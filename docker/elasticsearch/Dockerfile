ARG ELASTICSEARCH_VERSION=9.4.2
FROM docker.elastic.co/elasticsearch/elasticsearch:${ELASTICSEARCH_VERSION}

# SmartCN 在 GitHub Runner 构建镜像时一次性安装。生产容器启动时不再联网，
# 从而保证不能访问境外网络的腾讯云服务器也能反复重建 Elasticsearch。
RUN bin/elasticsearch-plugin install --batch analysis-smartcn \
    && bin/elasticsearch-plugin list | grep -qx analysis-smartcn

