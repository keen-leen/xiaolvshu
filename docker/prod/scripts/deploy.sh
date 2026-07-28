#!/usr/bin/env bash
set -Eeuo pipefail

if (( $# != 2 )); then
    echo "用法: $0 <后端 sha-*|keep> <前端 sha-*|keep>" >&2
    exit 2
fi

requested_backend_tag="$1"
requested_frontend_tag="$2"

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
prod_dir="$(cd -- "${script_dir}/.." && pwd)"
backend_state_file="${prod_dir}/.deployed-backend-image-tag"
frontend_state_file="${prod_dir}/.deployed-frontend-image-tag"
legacy_state_file="${prod_dir}/.deployed-image-tag"
compose=(docker compose --env-file "${prod_dir}/.env" -f "${prod_dir}/docker-compose.yml")

cd "${prod_dir}"
"${script_dir}/preflight.sh"

is_image_tag() {
    [[ "$1" =~ ^sha-[0-9a-f]{12}$ ]]
}

validate_requested_tag() {
    local component="$1"
    local image_tag="$2"
    if [[ "${image_tag}" != "keep" ]] && ! is_image_tag "${image_tag}"; then
        echo "${component}镜像标签非法: ${image_tag}，只允许 keep 或 sha-<12位小写十六进制提交号>。" >&2
        exit 2
    fi
}

read_deployed_tag() {
    local state_file="$1"
    local image_tag=""
    if [[ -f "${state_file}" ]]; then
        image_tag="$(tr -d '[:space:]' < "${state_file}")"
        if is_image_tag "${image_tag}"; then
            printf '%s\n' "${image_tag}"
            return 0
        fi
    fi
    if [[ -f "${legacy_state_file}" ]]; then
        image_tag="$(tr -d '[:space:]' < "${legacy_state_file}")"
        if is_image_tag "${image_tag}"; then
            printf '%s\n' "${image_tag}"
            return 0
        fi
    fi
    return 1
}

resolve_target_tag() {
    local component="$1"
    local requested_tag="$2"
    local deployed_tag="$3"
    if [[ "${requested_tag}" == "keep" ]]; then
        if ! is_image_tag "${deployed_tag}"; then
            echo "首次部署不能对${component}使用 keep；服务器还没有已记录的${component}版本。" >&2
            exit 2
        fi
        printf '%s\n' "${deployed_tag}"
        return 0
    fi
    printf '%s\n' "${requested_tag}"
}

write_deployed_tags() {
    local backend_tag="$1"
    local frontend_tag="$2"
    printf '%s\n' "${backend_tag}" > "${backend_state_file}.tmp"
    printf '%s\n' "${frontend_tag}" > "${frontend_state_file}.tmp"
    mv "${backend_state_file}.tmp" "${backend_state_file}"
    mv "${frontend_state_file}.tmp" "${frontend_state_file}"
}

validate_requested_tag "后端" "${requested_backend_tag}"
validate_requested_tag "前端" "${requested_frontend_tag}"
if [[ "${requested_backend_tag}" == "keep" && "${requested_frontend_tag}" == "keep" ]]; then
    echo "后端和前端不能同时使用 keep。" >&2
    exit 2
fi

previous_backend_tag="$(read_deployed_tag "${backend_state_file}" || true)"
previous_frontend_tag="$(read_deployed_tag "${frontend_state_file}" || true)"
target_backend_tag="$(
    resolve_target_tag "后端" "${requested_backend_tag}" "${previous_backend_tag}"
)"
target_frontend_tag="$(
    resolve_target_tag "前端" "${requested_frontend_tag}" "${previous_frontend_tag}"
)"

wait_for_application() {
    local attempt
    local frontend_address
    for attempt in {1..60}; do
        frontend_address="$("${compose[@]}" port frontend 80 2>/dev/null || true)"
        if [[ -n "${frontend_address}" ]] \
            && curl --fail --silent --show-error "http://${frontend_address}/healthz" >/dev/null 2>&1 \
            && "${compose[@]}" exec -T backend \
                curl --fail --silent --show-error http://127.0.0.1:8080/api/auth/health >/dev/null 2>&1; then
            return 0
        fi
        sleep 5
    done
    return 1
}

apply_tags() {
    local backend_tag="$1"
    local frontend_tag="$2"
    echo "部署后端 ${backend_tag}，前端 ${frontend_tag}。"
    BACKEND_IMAGE_TAG="${backend_tag}" FRONTEND_IMAGE_TAG="${frontend_tag}" "${compose[@]}" pull
    BACKEND_IMAGE_TAG="${backend_tag}" FRONTEND_IMAGE_TAG="${frontend_tag}" \
        "${compose[@]}" up -d --remove-orphans
    BACKEND_IMAGE_TAG="${backend_tag}" FRONTEND_IMAGE_TAG="${frontend_tag}" \
        wait_for_application
}

if apply_tags "${target_backend_tag}" "${target_frontend_tag}"; then
    write_deployed_tags "${target_backend_tag}" "${target_frontend_tag}"
    docker image prune --force >/dev/null
    echo "部署成功: 后端 ${target_backend_tag}，前端 ${target_frontend_tag}"
    exit 0
fi

echo "部署失败: 后端 ${target_backend_tag}，前端 ${target_frontend_tag}。" >&2
BACKEND_IMAGE_TAG="${target_backend_tag}" FRONTEND_IMAGE_TAG="${target_frontend_tag}" \
    "${compose[@]}" ps >&2 || true
BACKEND_IMAGE_TAG="${target_backend_tag}" FRONTEND_IMAGE_TAG="${target_frontend_tag}" \
    "${compose[@]}" logs --tail=120 backend frontend >&2 || true

if is_image_tag "${previous_backend_tag}" \
    && is_image_tag "${previous_frontend_tag}" \
    && [[ "${previous_backend_tag}" != "${target_backend_tag}" \
        || "${previous_frontend_tag}" != "${target_frontend_tag}" ]]; then
    echo "开始回滚到后端 ${previous_backend_tag}，前端 ${previous_frontend_tag}。" >&2
    if apply_tags "${previous_backend_tag}" "${previous_frontend_tag}"; then
        write_deployed_tags "${previous_backend_tag}" "${previous_frontend_tag}"
        echo "自动回滚成功。" >&2
    else
        echo "自动回滚也失败，需要立即登录服务器检查。" >&2
    fi
else
    echo "没有可用的上一版本，无法自动回滚。" >&2
fi

exit 1
