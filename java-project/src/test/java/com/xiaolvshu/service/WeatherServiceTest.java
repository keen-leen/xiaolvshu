package com.xiaolvshu.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WeatherServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReturnCurrentAndSevenDayWeatherAsReadableTextWithoutCredentials() {
        TestClients clients = createClients();
        WeatherService weatherService = new WeatherService(
                clients.geocodingClient(), clients.forecastClient(), objectMapper);

        clients.geocodingServer().expect(once(), requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("/v1/search"),
                        org.hamcrest.Matchers.containsString("name=%E6%9D%AD%E5%B7%9E"))))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("count", "1"))
                .andExpect(queryParam("language", "zh"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("""
                        {"results":[{
                          "name":"杭州","latitude":30.29365,"longitude":120.16142,
                          "timezone":"Asia/Shanghai","admin1":"浙江","country":"中国"
                        }]}
                        """, MediaType.APPLICATION_JSON));

        clients.forecastServer().expect(once(), requestTo(org.hamcrest.Matchers.containsString("/v1/forecast")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("latitude", "30.29365"))
                .andExpect(queryParam("longitude", "120.16142"))
                .andExpect(queryParam("timezone", "Asia/Shanghai"))
                .andExpect(queryParam("forecast_days", "7"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("""
                        {
                          "current":{
                            "time":"2026-07-26T16:00","temperature_2m":32.1,
                            "apparent_temperature":35.2,"relative_humidity_2m":60,
                            "weather_code":0,"wind_speed_10m":9.5,"wind_direction_10m":90
                          },
                          "daily":{
                            "time":["2026-07-26","2026-07-27"],
                            "weather_code":[2,61],
                            "temperature_2m_max":[34.0,31.0],
                            "temperature_2m_min":[26.0,25.0]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        String result = weatherService.getWeather("杭州");

        assertThat(result)
                .contains("地点：杭州，浙江，中国")
                .contains("当前天气：晴，32.1℃，体感 35.2℃，湿度 60%，东风 9.5 km/h")
                .contains("2026-07-26：局部多云，26.0～34.0℃")
                .contains("2026-07-27：雨，25.0～31.0℃")
                .contains("数据来源：Open-Meteo（已整理为中文）")
                .contains("地点数据：GeoNames");
        clients.verify();
    }

    @Test
    void shouldReturnClearMessageWhenLocationDoesNotExist() {
        TestClients clients = createClients();
        WeatherService weatherService = new WeatherService(
                clients.geocodingClient(), clients.forecastClient(), objectMapper);
        clients.geocodingServer().expect(once(), requestTo(org.hamcrest.Matchers.containsString("/v1/search")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(weatherService.getWeather("不存在的地点"))
                .isEqualTo("天气查询失败：没有找到地点“不存在的地点”。");
        clients.verify();
    }

    @Test
    void shouldReturnSimpleMessageWhenProviderFails() {
        TestClients clients = createClients();
        WeatherService weatherService = new WeatherService(
                clients.geocodingClient(), clients.forecastClient(), objectMapper);
        clients.geocodingServer().expect(once(), requestTo(org.hamcrest.Matchers.containsString("/v1/search")))
                .andRespond(withServerError());

        assertThat(weatherService.getWeather("杭州")).contains("天气查询暂时不可用");
        clients.verify();
    }

    private TestClients createClients() {
        RestClient.Builder geocodingBuilder = RestClient.builder().baseUrl("https://geocoding.test");
        RestClient.Builder forecastBuilder = RestClient.builder().baseUrl("https://forecast.test");
        MockRestServiceServer geocodingServer = MockRestServiceServer.bindTo(geocodingBuilder).build();
        MockRestServiceServer forecastServer = MockRestServiceServer.bindTo(forecastBuilder).build();
        return new TestClients(
                geocodingBuilder.build(),
                forecastBuilder.build(),
                geocodingServer,
                forecastServer);
    }

    private record TestClients(
            RestClient geocodingClient,
            RestClient forecastClient,
            MockRestServiceServer geocodingServer,
            MockRestServiceServer forecastServer) {

        private void verify() {
            geocodingServer.verify();
            forecastServer.verify();
        }
    }
}
