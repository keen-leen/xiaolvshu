# 腾讯云生产部署

本文按实际操作顺序说明如何将小旅书部署到一台全新的 Ubuntu 24.04 腾讯云服务器。
服务器初始化、Docker、Nginx、Certbot 和首次证书签发全部由管理员手动完成；GitHub
Actions 只负责重复发生的测试、镜像构建、应用发布和回滚。

## 一、理解最终架构

```text
本地 push main
  -> GitHub CI 自动测试
  -> 人工运行 Release
  -> GitHub 构建 linux/amd64 镜像并推送腾讯云 TCR
  -> SSH 登录 deploy 用户
  -> Docker Compose 拉取镜像、更新应用、健康检查

公网 80/443
  -> 宿主机 Nginx：HTTPS、证书、HTTP 跳转
  -> 127.0.0.1:18080
  -> 前端容器 Nginx：Vue 静态文件、/api、SSE
  -> backend:8080
```

宿主机 Nginx 和证书不属于应用发布。应用回滚不会修改 Nginx，也不会修改
`/etc/letsencrypt`。

## 二、准备 GitHub 和 TCR

### 1. 确认 TCR 仓库

腾讯云 TCR 私有命名空间需要以下仓库：

- `xiaolvshu-backend`
- `xiaolvshu-frontend`
- `elasticsearch-smartcn`
- `mysql`
- `redis`
- `rabbitmq`

基础镜像未同步时，手动运行 GitHub Actions 中的
`Sync infrastructure images to TCR`。服务器运行时只从 TCR 拉取镜像。

### 2. 准备部署 SSH 密钥

在可信本地机器生成一把只供 GitHub Actions 使用的密钥：

```bash
ssh-keygen -t ed25519 -C xiaolvshu-github-actions -f ~/.ssh/xiaolvshu_github_actions
```

不要覆盖已有密钥。生成后：

- `~/.ssh/xiaolvshu_github_actions.pub` 稍后放到服务器。
- `~/.ssh/xiaolvshu_github_actions` 稍后保存为 GitHub Secret。

## 三、手动初始化服务器

以下命令均在服务器上逐条执行。每完成一节，先完成对应验收，再继续下一节。

### 1. 检查系统

使用腾讯云控制台提供的初始账号登录服务器：

```bash
uname -m
cat /etc/os-release
free -h
df -h /
```

验收：

- 架构必须为 `x86_64`。
- 系统为 Ubuntu 24.04。
- 根分区至少保留 8 GiB 可用空间。

### 2. 创建部署用户

```bash
sudo adduser --disabled-password --gecos '' deploy
sudo install -d -o deploy -g deploy -m 700 /home/deploy/.ssh
sudo install -d -o deploy -g deploy -m 750 /opt/xiaolvshu
```

将本地 `xiaolvshu_github_actions.pub` 的完整内容写入服务器：

```bash
sudoedit /home/deploy/.ssh/authorized_keys
sudo chown deploy:deploy /home/deploy/.ssh/authorized_keys
sudo chmod 600 /home/deploy/.ssh/authorized_keys
```

从本地另开终端验证，先不要关闭当前服务器会话：

```bash
ssh -i ~/.ssh/xiaolvshu_github_actions deploy@SERVER
```

只有新登录成功后，才能继续配置 GitHub SSH。

### 3. 安装 Docker 和 Compose

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker deploy
```

退出 `deploy` 会话并重新登录，使 Docker 组生效：

```bash
docker version
docker compose version
docker info
```

Docker 组具有接近 root 的权限，只应加入受信任的部署账号。

### 4. 设置 Elasticsearch 系统参数

```bash
echo 'vm.max_map_count=262144' \
  | sudo tee /etc/sysctl.d/99-xiaolvshu.conf
sudo sysctl --system
sysctl vm.max_map_count
```

输出必须至少为 `262144`。

### 5. 可选：为 4G 服务器增加 2G swap

先检查：

```bash
swapon --show
```

只有当前没有 swap 时才执行一次：

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h
```

swap 只是降低突发 OOM 风险。持续使用 swap 时，应升级服务器或外置 Elasticsearch。

### 6. 登录 TCR

切换到 `deploy` 用户：

```bash
docker login ccr.ccs.tencentyun.com
```

输入 TCR 用户名和访问密码，然后验证：

```bash
cat ~/.docker/config.json
```

只确认 Registry 地址存在，不要复制或公开其中的认证内容。

## 四、准备生产环境变量

从可信本地机器上传模板：

```bash
ssh -i ~/.ssh/xiaolvshu_github_actions deploy@SERVER \
  'mkdir -p /opt/xiaolvshu/docker/prod'

scp -i ~/.ssh/xiaolvshu_github_actions \
  docker/prod/.env.example \
  deploy@SERVER:/opt/xiaolvshu/docker/prod/.env
```

登录服务器：

```bash
chmod 600 /opt/xiaolvshu/docker/prod/.env
nano /opt/xiaolvshu/docker/prod/.env
```

逐项填写：

- TCR 命名空间。
- MySQL、RabbitMQ、Elasticsearch 密码。
- JWT、DashScope 和腾讯云 COS 密钥。
- `APP_CORS_ALLOWED_ORIGINS=https://主域名,https://www.主域名`。

`BACKEND_IMAGE_TAG` 和 `FRONTEND_IMAGE_TAG` 的示例值不需要修改，发布工作流会覆盖。

检查占位符：

```bash
grep -n 'replace_with_' /opt/xiaolvshu/docker/prod/.env
```

输出必须为空。真实 `.env` 只保存在服务器，不上传 GitHub。

## 五、配置 GitHub Actions

进入 GitHub 仓库：

```text
Settings -> Secrets and variables -> Actions
```

配置 Repository Variables：

| 名称 | 内容 |
| --- | --- |
| `TCR_REGISTRY` | `ccr.ccs.tencentyun.com` |
| `TCR_NAMESPACE` | TCR 私有命名空间 |
| `PROD_HOST` | 服务器公网 IP |
| `PROD_SSH_PORT` | SSH 端口，通常为 `22` |
| `PROD_USER` | `deploy` |
| `PROD_DEPLOY_PATH` | `/opt/xiaolvshu` |

配置 Repository Secrets：

| 名称 | 内容 |
| --- | --- |
| `TCR_USERNAME` | TCR 用户名 |
| `TCR_PASSWORD` | TCR 访问密码 |
| `PROD_SSH_KEY` | `xiaolvshu_github_actions` 私钥全文 |
| `PROD_HOST_KEY` | 核对过指纹的 SSH known_hosts 记录 |

获取 `PROD_HOST_KEY` 前，先在服务器腾讯云控制台查看真实指纹：

```bash
sudo ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
```

再在本地执行：

```bash
ssh-keyscan -p 22 -t ed25519 SERVER > /tmp/xiaolvshu-known-hosts
ssh-keygen -lf /tmp/xiaolvshu-known-hosts
```

两边指纹一致后，将 `/tmp/xiaolvshu-known-hosts` 的完整内容保存为
`PROD_HOST_KEY`。不要关闭 SSH 主机校验。

## 六、第一次通过 CI/CD 发布

### 1. 推送 CI/CD

提交并 push 当前 CI/CD 和生产部署文件。push `main` 只会运行 `CI`，不会自动部署。

等待后端和前端检查全部成功。

### 2. 手动发布

进入 GitHub Actions，选择 `Build, publish and deploy`：

- 必须从 `main` 分支运行。
- 第一次 `release_scope` 选择 `all`。

Release 会对同一提交重新运行 CI，然后：

1. 构建后端和前端 `linux/amd64` 镜像。
2. 推送 `sha-<提交号>` 和便于查看的 `latest` 到 TCR。
3. 上传生产 Compose、部署脚本和数据库结构。
4. 在服务器执行 `docker compose pull` 和 `up -d`。
5. 检查前端和后端容器。
6. 失败时尝试恢复上一成功镜像。

第一次发布还没有上一版本，因此失败时不能自动回滚。

### 3. 通过 SSH 隧道验收

本地执行：

```bash
ssh -i ~/.ssh/xiaolvshu_github_actions \
  -L 18080:127.0.0.1:18080 \
  deploy@SERVER
```

访问：

```text
http://127.0.0.1:18080
```

服务器检查：

```bash
curl -fsS http://127.0.0.1:18080/healthz

cd /opt/xiaolvshu/docker/prod
BACKEND_IMAGE_TAG="$(cat .deployed-backend-image-tag)" \
FRONTEND_IMAGE_TAG="$(cat .deployed-frontend-image-tag)" \
docker compose --env-file .env ps
```

应用通过隧道正常后，再配置公网 Nginx 和 HTTPS。

## 七、手动安装宿主机 Nginx

### 1. 安装并启动

```bash
sudo apt update
sudo apt install -y nginx
sudo systemctl enable --now nginx
sudo systemctl status nginx --no-pager
```

腾讯云安全组开放 TCP 80 和 443。MySQL、Redis、RabbitMQ、Elasticsearch 和后端端口
不要开放。

### 2. 创建 HTTP 配置

创建 ACME 验证目录：

```bash
sudo install -d -o www-data -g www-data -m 755 /var/www/certbot
```

编辑：

```bash
sudo nano /etc/nginx/sites-available/xiaolvshu
```

先写入以下 HTTP 配置，将两个域名替换为真实值：

```nginx
server {
    listen 80;
    listen [::]:80;
    server_name example.com www.example.com;

    client_max_body_size 500m;

    location ^~ /.well-known/acme-challenge/ {
        root /var/www/certbot;
        try_files $uri =404;
    }

    location / {
        proxy_pass http://127.0.0.1:18080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto http;
        proxy_buffering off;
        proxy_read_timeout 180s;
    }
}
```

启用并检查：

```bash
sudo ln -s /etc/nginx/sites-available/xiaolvshu \
  /etc/nginx/sites-enabled/xiaolvshu
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

此时先访问 `http://主域名/healthz`。HTTP 正常后再签发证书。

## 八、手动签发 HTTPS 证书

### 1. 检查 DNS

在本地检查主域名和 `www` 都解析到服务器公网 IP：

```bash
nslookup example.com
nslookup www.example.com
```

DNS 不正确时不要重复请求证书。

### 2. 安装 Certbot 并签发

服务器执行：

```bash
sudo apt install -y certbot

sudo certbot certonly \
  --webroot \
  --webroot-path /var/www/certbot \
  --domain example.com \
  --domain www.example.com \
  --email YOUR_EMAIL \
  --agree-tos \
  --no-eff-email
```

签发成功后检查：

```bash
sudo certbot certificates
sudo ls -l /etc/letsencrypt/live/example.com/
```

### 3. 手动切换 Nginx 到 HTTPS

再次编辑 `/etc/nginx/sites-available/xiaolvshu`，替换为：

```nginx
server {
    listen 80;
    listen [::]:80;
    server_name example.com www.example.com;

    location ^~ /.well-known/acme-challenge/ {
        root /var/www/certbot;
        try_files $uri =404;
    }

    location / {
        return 301 https://example.com$request_uri;
    }
}

server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name www.example.com;

    ssl_certificate /etc/letsencrypt/live/example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/example.com/privkey.pem;

    return 301 https://example.com$request_uri;
}

server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name example.com;

    ssl_certificate /etc/letsencrypt/live/example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/example.com/privkey.pem;

    client_max_body_size 500m;

    location / {
        proxy_pass http://127.0.0.1:18080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto https;
        proxy_buffering off;
        proxy_read_timeout 180s;
    }
}
```

检查后再重载：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

验收：

```bash
curl -I http://example.com
curl -I https://example.com
curl -I https://www.example.com
```

预期：

- HTTP 跳转到主域名 HTTPS。
- `www` HTTPS 跳转到主域名 HTTPS。
- 主域名返回有效证书和应用响应。

### 4. 检查自动续期

Ubuntu 的 Certbot 包会提供 systemd timer：

```bash
systemctl list-timers | grep certbot
sudo certbot renew --dry-run
```

为确保续期后 Nginx 重新读取证书，手动创建部署钩子：

```bash
sudo nano /etc/letsencrypt/renewal-hooks/deploy/reload-nginx
```

内容：

```bash
#!/usr/bin/env bash
set -Eeuo pipefail
nginx -t
systemctl reload nginx
```

设置权限：

```bash
sudo chmod 755 /etc/letsencrypt/renewal-hooks/deploy/reload-nginx
sudo certbot renew --dry-run
```

证书续期由服务器完成，不进入 GitHub Actions。

## 九、日常发布和回滚

日常发布：

1. push `main`。
2. 等待自动 CI 通过。
3. 手动运行 `Build, publish and deploy`。
4. 前后端都修改选 `all`；只修改一端则选对应范围。
5. 浏览器访问正式 HTTPS 域名验收。

生产始终部署 `sha-*` 标签。`latest` 只方便浏览，不能用于生产版本判断。

需要回滚时，手动运行 `Roll back production`：

- 要回滚的应用填写历史 `sha-*`。
- 不回滚的应用填写 `keep`。
- 两个输入不能同时为 `keep`。

回滚只切换应用镜像，不删除数据卷，不修改 Nginx 和证书。数据库不兼容变更必须单独进行
备份、迁移和回退设计。
