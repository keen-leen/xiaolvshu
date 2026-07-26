package com.xiaolvshu.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelAgentConfigurationTest {

    @Test
    void shouldDelegateConcreteCitationRulesToSuccessfulToolResults() {
        assertTrue(TravelAgentConfiguration.SYSTEM_PROMPT
                .contains("社区检索工具成功返回资料时，必须严格遵守工具结果中由应用提供的引用规则和合法尾注集合"));
        assertFalse(TravelAgentConfiguration.SYSTEM_PROMPT
                .contains("必须保留工具结果中的 [S1]、[S2] 来源编号"));
    }

    @Test
    void shouldRequireWeatherToolForRealtimeWeather() {
        assertTrue(TravelAgentConfiguration.SYSTEM_PROMPT
                .contains("查询实时天气或未来7日天气时必须调用 get_weather"));
    }
}
