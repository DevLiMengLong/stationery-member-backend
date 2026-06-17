package com.gechuang.stationery.member.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

class MemberUpdateRequestTest {

    private final Validator validator;

    MemberUpdateRequestTest() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    @Test
    void updateBodyShouldNotRequireIdBecauseControllerUsesPathVariable() {
        MemberUpdateRequest request = new MemberUpdateRequest();
        request.setName("Updated Member");
        request.setPoints(125);
        request.setStatus(0);

        assertThat(validator.validate(request)).isEmpty();
    }
}
