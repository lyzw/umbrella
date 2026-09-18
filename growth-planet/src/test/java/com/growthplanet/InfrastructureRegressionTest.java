package com.growthplanet;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.growthplanet.config.JwtConfig;
import com.growthplanet.dto.request.ChildProfileReq;
import com.growthplanet.entity.ChildProfile;
import com.growthplanet.util.JwtUtil;
import jakarta.validation.Validation;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfrastructureRegressionTest {

    @Test
    void loginResponsePreservesIsNewProperty() {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var response = com.growthplanet.dto.response.WxLoginResp.builder()
                .token("synthetic").isNew(true).expiresIn(1800).build();
        String json = mapper.writeValueAsString(response);
        assertTrue(mapper.readTree(json).get("isNew").asBoolean());
        assertFalse(mapper.readTree(json).has("new"));
        assertTrue(mapper.readValue(json, com.growthplanet.dto.response.WxLoginResp.class).isNew());
    }

    @Test
    void jwtComponentStartsWithConfiguredConstructor() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource("test", java.util.Map.of(
                            "jwt.secret", "synthetic-only-test-secret-01234567890123456789012345")));
            context.register(JwtConfig.class, JwtUtil.class);
            context.refresh();
            assertNotNull(context.getBean(JwtUtil.class));
        }
    }

    @Test
    void childJsonFieldsHaveResultMap() {
        var configuration = new MybatisConfiguration();
        var assistant = new MapperBuilderAssistant(configuration, "regression");
        assistant.setCurrentNamespace("com.growthplanet.mapper.ChildProfileMapper");
        var table = TableInfoHelper.initTableInfo(assistant, ChildProfile.class);
        assertTrue(table.isAutoInitResultMap());
        assertNotNull(table.getResultMap());
    }

    @Test
    void emptyProfileCannotPassValidation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertFalse(factory.getValidator().validate(new ChildProfileReq()).isEmpty());
        }
    }

    @Test
    void falseOrMissingConsentAndApprovalAreInvalid() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var consent = new com.growthplanet.dto.request.ConsentReq();
            consent.setAgreed(false);
            assertFalse(factory.getValidator().validate(consent).isEmpty());
            assertFalse(factory.getValidator().validate(new com.growthplanet.dto.request.BindApproveReq()).isEmpty());
        }
    }

    @Test
    void responsePreservesStringBusinessIdsAndRequestId() {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var response = com.growthplanet.dto.response.CreateFamilyResp.builder()
                .familyId(9007199254740993L).expireAt(12345L).build();
        var json = mapper.readTree(mapper.writeValueAsString(com.growthplanet.common.result.Result.ok(response)));
        assertTrue(json.get("data").get("familyId").isString());
        assertTrue(json.get("data").get("expireAt").isNumber());
        assertNotNull(json.get("requestId"));
        org.junit.jupiter.api.Assertions.assertEquals("E-009",
                com.growthplanet.common.result.ResultCode.E009_FORBIDDEN.getCode());
    }
}
