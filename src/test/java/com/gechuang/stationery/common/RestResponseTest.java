package com.gechuang.stationery.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RestResponseTest {

    @Test
    void successShouldWrapData() {
        RestResponse<String> response = RestResponse.success("ok");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("0");
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isEqualTo("ok");
        assertThat(response.getTraceId()).isNotBlank();
        assertThat(response.getTimestamp()).isNotBlank();
    }

    @Test
    void failShouldExposeBusinessCodeAndMessage() {
        RestResponse<Void> response = RestResponse.fail("400", "bad request");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("400");
        assertThat(response.getMessage()).isEqualTo("bad request");
        assertThat(response.getData()).isNull();
    }
}
