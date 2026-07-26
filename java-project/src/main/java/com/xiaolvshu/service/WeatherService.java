package com.xiaolvshu.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Open-Meteo 天气查询服务。
 *
 * 该类只负责天气业务：查询地点坐标、获取当前天气和未来 7 日预报，并整理成模型容易理解的中文文本。
 * Agent 工具注解放在 TravelAgentTools 中，避免外部接口实现与模型工具定义混在一起。
 *
 * 当前使用无需密钥的 Open-Meteo 非商业开放接口，因此不保留供应商凭据和鉴权代码。
 * 项目只接入一个天气供应商，不额外设计 Provider 接口、实现类或天气 DTO。
 */
@Service
@Slf4j
public class WeatherService {

    private static final String GEOCODING_API = "https://geocoding-api.open-meteo.com";
    private static final String FORECAST_API = "https://api.open-meteo.com";
    private static final String CURRENT_FIELDS = String.join(",",
            "temperature_2m",
            "apparent_temperature",
            "relative_humidity_2m",
            "weather_code",
            "wind_speed_10m",
            "wind_direction_10m");
    private static final String DAILY_FIELDS = String.join(",",
            "weather_code",
            "temperature_2m_max",
            "temperature_2m_min");
    private static final String SOURCE = """
            数据来源：Open-Meteo（已整理为中文）：https://open-meteo.com
            地点数据：GeoNames：https://www.geonames.org
            """.trim();

    private final RestClient geocodingClient;
    private final RestClient forecastClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public WeatherService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this(
                buildRestClient(restClientBuilder, GEOCODING_API),
                buildRestClient(restClientBuilder, FORECAST_API),
                objectMapper);
    }

    WeatherService(
            RestClient geocodingClient,
            RestClient forecastClient,
            ObjectMapper objectMapper) {
        this.geocodingClient = geocodingClient;
        this.forecastClient = forecastClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询指定地点的当前天气和未来 7 日预报。
     *
     * 地点错误直接返回明确提示，供应商异常返回统一的暂不可用提示。
     * 这样 Agent 始终得到可读文本，不会接触 HTTP 异常或供应商原始响应。
     */
    public String getWeather(String location) {
        if (location == null || location.isBlank()) {
            return "天气查询失败：地点不能为空。";
        }

        String normalizedLocation = location.trim();
        try {
            // Open-Meteo 天气接口使用经纬度，因此先把用户输入转换为地点坐标。
            JsonNode city = findLocation(normalizedLocation);
            if (city.isMissingNode()) {
                return "天气查询失败：没有找到地点“" + normalizedLocation + "”。";
            }

            // 当前天气和未来 7 日可以由同一个 Forecast 请求返回，不需要拆成多个接口调用。
            JsonNode forecast = requestForecast(city);
            return formatResult(city, forecast);
        } catch (Exception e) {
            log.warn("Open-Meteo 天气查询失败: {}", e.getMessage());
            return "天气查询暂时不可用，请明确告诉用户稍后再试，不要猜测实时天气。";
        }
    }

    private JsonNode findLocation(String location) throws Exception {
        String body = geocodingClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v1/search")
                        .queryParam("name", location)
                        .queryParam("count", 1)
                        .queryParam("language", "zh")
                        .queryParam("format", "json")
                        .build())
                .retrieve()
                .body(String.class);
        JsonNode response = objectMapper.readTree(body);
        return response.path("results").path(0);
    }

    private JsonNode requestForecast(JsonNode city) throws Exception {
        if (!city.hasNonNull("latitude") || !city.hasNonNull("longitude")) {
            throw new IllegalStateException("地点响应缺少经纬度");
        }
        String cityTimezone = city.path("timezone").asText();
        String timezone = cityTimezone.isBlank() ? "auto" : cityTimezone;

        String body = forecastClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v1/forecast")
                        .queryParam("latitude", city.path("latitude").asDouble())
                        .queryParam("longitude", city.path("longitude").asDouble())
                        .queryParam("current", CURRENT_FIELDS)
                        .queryParam("daily", DAILY_FIELDS)
                        .queryParam("timezone", timezone)
                        .queryParam("forecast_days", 7)
                        .build())
                .retrieve()
                .body(String.class);
        JsonNode response = objectMapper.readTree(body);
        if (response.path("error").asBoolean(false)) {
            throw new IllegalStateException("天气接口返回错误：" + response.path("reason").asText());
        }
        return response;
    }

    private String formatResult(JsonNode city, JsonNode forecast) {
        // JsonNode 只读取回答需要的字段，避免为供应商响应创建多层 DTO。
        JsonNode current = forecast.path("current");
        String cityName = city.path("name").asText();
        String admin2 = city.path("admin2").asText();
        String admin1 = city.path("admin1").asText();
        String country = city.path("country").asText();

        StringBuilder result = new StringBuilder()
                .append("地点：").append(cityName);
        appendDistinctArea(result, admin2, cityName);
        appendDistinctArea(result, admin1, cityName, admin2);
        appendDistinctArea(result, country, cityName, admin2, admin1);

        result.append("\n更新时间：").append(current.path("time").asText())
                .append("\n当前天气：").append(weatherDescription(current.path("weather_code").asInt(-1)))
                .append("，").append(current.path("temperature_2m").asText()).append("℃")
                .append("，体感 ").append(current.path("apparent_temperature").asText()).append("℃")
                .append("，湿度 ").append(current.path("relative_humidity_2m").asText()).append("%")
                .append("，").append(windDirection(current.path("wind_direction_10m").asDouble()))
                .append(" ").append(current.path("wind_speed_10m").asText()).append(" km/h")
                .append("\n未来7日：");

        JsonNode daily = forecast.path("daily");
        JsonNode dates = daily.path("time");
        for (int index = 0; index < dates.size(); index++) {
            result.append("\n- ").append(dates.path(index).asText())
                    .append("：").append(weatherDescription(daily.path("weather_code").path(index).asInt(-1)))
                    .append("，").append(daily.path("temperature_2m_min").path(index).asText())
                    .append("～").append(daily.path("temperature_2m_max").path(index).asText()).append("℃");
        }
        return result.append("\n").append(SOURCE).toString();
    }

    private void appendDistinctArea(StringBuilder result, String area, String... existingAreas) {
        if (area == null || area.isBlank()) {
            return;
        }
        for (String existing : existingAreas) {
            if (area.equals(existing)) {
                return;
            }
        }
        result.append("，").append(area);
    }

    /**
     * Open-Meteo 使用 WMO 天气代码，工具结果需要转换为模型和用户都容易理解的中文。
     */
    private String weatherDescription(int code) {
        return switch (code) {
            case 0 -> "晴";
            case 1 -> "大部晴朗";
            case 2 -> "局部多云";
            case 3 -> "阴";
            case 45, 48 -> "雾";
            case 51, 53, 55 -> "毛毛雨";
            case 56, 57 -> "冻毛毛雨";
            case 61, 63, 65 -> "雨";
            case 66, 67 -> "冻雨";
            case 71, 73, 75 -> "雪";
            case 77 -> "米雪";
            case 80, 81, 82 -> "阵雨";
            case 85, 86 -> "阵雪";
            case 95 -> "雷暴";
            case 96, 99 -> "雷暴伴冰雹";
            default -> "未知天气";
        };
    }

    /**
     * Forecast API 返回角度风向，这里按每 45 度映射为八方位名称。
     */
    private String windDirection(double degrees) {
        String[] directions = {"北风", "东北风", "东风", "东南风", "南风", "西南风", "西风", "西北风"};
        double normalized = (degrees % 360 + 360) % 360;
        int index = (int) Math.floor((normalized + 22.5) / 45) % directions.length;
        return directions[index];
    }

    private static RestClient buildRestClient(RestClient.Builder builder, String baseUrl) {
        // 连接和读取统一限制为 3 秒，避免天气供应商阻塞 Agent 的流式响应。
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
