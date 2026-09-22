package com.gechuang.stationery.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionBindingConfigurationTest {

    @Test
    void applicationBindingIsEnvironmentControlledAndLoopbackByDefault() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));

        assertThat(sources).isNotEmpty();
        assertThat(sources.get(0).getProperty("server.address"))
                .isEqualTo("${SERVER_ADDRESS:127.0.0.1}");
        assertThat(sources.get(0).getProperty("server.port"))
                .isEqualTo("${SERVER_PORT:8090}");
    }
}
