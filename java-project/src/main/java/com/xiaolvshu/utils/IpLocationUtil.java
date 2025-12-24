package com.xiaolvshu.utils;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP地址工具类
 */
@Slf4j
@Component
public class IpLocationUtil {
    
    private static final int TIMEOUT = 10000; // 10秒超时
    private static final int BACKUP_TIMEOUT = 5000; // 备用接口5秒超时
    
    /**
     * 获取请求的真实IP地址
     * @param request HttpServletRequest对象
     * @return IP地址
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (ip != null && ip.length() != 0 && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能是多个 IP，取第一个
            return ip.split(",")[0].trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (ip != null && ip.length() != 0 && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        ip = request.getHeader("Proxy-Client-IP");
        if (ip != null && ip.length() != 0 && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        ip = request.getHeader("WL-Proxy-Client-IP");
        if (ip != null && ip.length() != 0 && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        // 处理IPv4映射的IPv6地址格式，去掉::ffff:前缀
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr != null && remoteAddr.startsWith("::ffff:")) {
            remoteAddr = remoteAddr.substring(7); // 去掉'::ffff:'前缀
        }
        
        return remoteAddr;
    }

    /**
     * 根据IP地址获取地理位置信息
     * @param ip IP地址
     * @return 省份信息
     */
    public static String getIpLocation(String ip) {
        try {
            // 如果是本地IP，返回默认值
            if (isLocalIp(ip)) {
                return "本地";
            }

            // 调用IP属地API
            String url = "https://api.pearktrue.cn/api/ip/details";
            HttpResponse response = HttpRequest.get(url).form("ip", ip).timeout(TIMEOUT).execute();

            if (response.isOk()) {
                String body = response.body();
                JSONObject jsonObject = JSONUtil.parseObj(body);
                
                if (jsonObject.getInt("code") == 200 && jsonObject.containsKey("data")) {
                    JSONObject data = jsonObject.getJSONObject("data");
                    
                    // 根据API返回的数据结构提取省份信息
                    String location = null;
                    if (data.containsKey("subdivisions")) {
                        location = data.getStr("subdivisions");
                    } else if (data.containsKey("region")) {
                        location = data.getStr("region");
                    }
                    
                    if (location != null && !location.isEmpty()) {
                        return cleanLocationName(location);
                    }
                }
            }

            // 如果主接口返回未知，尝试备用接口
            return getIpLocationFromBackup(ip);
            
        } catch (Exception e) {
            log.error("获取IP属地失败: {}", e.getMessage());
            return "未知";
        }
    }

    /**
     * 使用备用接口获取IP地理位置
     * @param ip IP地址
     * @return 省份信息
     */
    private static String getIpLocationFromBackup(String ip) {
        try {
            String url = "https://api.pearktrue.cn/api/ip/high";
            HttpResponse response = HttpRequest.get(url).form("ip", ip).timeout(BACKUP_TIMEOUT).execute();

            if (response.isOk()) {
                String body = response.body();
                JSONObject jsonObject = JSONUtil.parseObj(body);
                
                if (jsonObject.getInt("code") == 200 && jsonObject.containsKey("data")) {
                    JSONObject data = jsonObject.getJSONObject("data");
                    
                    if (data.containsKey("province")) {
                        String province = data.getStr("province");
                        if (province != null && !province.isEmpty()) {
                            return cleanLocationName(province);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("备用IP属地接口调用失败: {}", e.getMessage());
        }
        
        return "未知";
    }

    /**
     * 判断是否为本地IP
     * @param ip IP地址
     * @return 是否为本地IP
     */
    private static boolean isLocalIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return true;
        }
        
        return ip.equals("127.0.0.1") || ip.equals("::1") || ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.");
    }

    /**
     * 清理地区名称，去掉省、市、自治区等后缀
     * @param location 原始地区名称
     * @return 清理后的地区名称
     */
    private static String cleanLocationName(String location) {
        if (location == null) {
            return "未知";
        }
        return location.replace("省", "").replace("壮族自治区", "").replace("回族自治区", "").replace("维吾尔自治区", "").replace("自治区", "").replace("特别行政区", "").replace("市", "");
    }
}