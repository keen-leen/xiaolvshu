#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
prod_dir="$(cd -- "${script_dir}/.." && pwd)"
cd "${prod_dir}"

required_commands=(docker curl awk grep sysctl df uname)
for command_name in "${required_commands[@]}"; do
    if ! command -v "${command_name}" >/dev/null 2>&1; then
        echo "缺少必要命令: ${command_name}" >&2
        exit 1
    fi
done

if ! docker compose version >/dev/null 2>&1; then
    echo "Docker Compose v2 不可用，请先安装 docker-compose-plugin。" >&2
    exit 1
fi

architecture="$(uname -m)"
if [[ "${architecture}" != "x86_64" ]]; then
    echo "服务器架构为 ${architecture}，当前生产镜像仅构建 linux/amd64。" >&2
    exit 1
fi

vm_max_map_count="$(sysctl -n vm.max_map_count 2>/dev/null || echo 0)"
if (( vm_max_map_count < 262144 )); then
    echo "vm.max_map_count=${vm_max_map_count}，Elasticsearch 要求至少 262144。" >&2
    echo "请执行: sudo sysctl -w vm.max_map_count=262144，并写入 /etc/sysctl.d/99-xiaolvshu.conf。" >&2
    exit 1
fi

available_kib="$(awk '/MemAvailable/ {print $2}' /proc/meminfo)"
if (( available_kib < 2500000 )); then
    echo "警告: 当前可用内存低于约 2.5 GiB，首次同时启动全部服务可能失败。" >&2
fi

available_disk_kib="$(df -Pk "${prod_dir}" | awk 'NR == 2 {print $4}')"
if (( available_disk_kib < 8388608 )); then
    echo "部署分区可用空间低于 8 GiB，无法安全容纳应用、Elasticsearch 镜像和数据增长。" >&2
    exit 1
fi

swap_kib="$(awk '/SwapTotal/ {print $2}' /proc/meminfo)"
if (( swap_kib < 1048576 )); then
    echo "警告: swap 低于 1 GiB；4G 单机在服务同时重启时更容易触发整机 OOM。" >&2
fi

if [[ ! -f .env ]]; then
    echo "缺少 $(pwd)/.env，请从 .env.example 创建并填写生产密钥。" >&2
    exit 1
fi

if grep -Eq 'replace_with_' .env; then
    echo ".env 仍包含密钥或命名空间占位值，请先完成生产配置。" >&2
    exit 1
fi

docker info >/dev/null
echo "生产服务器预检通过。"
