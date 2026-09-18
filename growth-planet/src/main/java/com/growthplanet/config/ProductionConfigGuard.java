package com.growthplanet.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** 防止将合成演示配置带入真实微信处理环境；审批内容仍须人工签核。 */
@Component
public class ProductionConfigGuard {
    private final Environment environment;
    private final JwtProperties jwt;
    private final ComplianceProperties compliance;

    public ProductionConfigGuard(Environment environment, JwtProperties jwt, ComplianceProperties compliance) {
        this.environment = environment;
        this.jwt = jwt;
        this.compliance = compliance;
    }

    @PostConstruct
    public void validate() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        if (environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            throw new IllegalStateException("prod cannot be combined with dev or test");
        }
        String secret = jwt.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32
                || isDemonstration(secret) || jwt.getAccessTtl() != 1_800_000L) {
            throw new IllegalStateException("prod requires an independent JWT secret and a 30-minute token lifetime");
        }
        if (compliance.isCollectionEnabled()) {
            compliance.requireCollection();
            if (isDemonstration(compliance.getApprovalReference())
                    || isDemonstration(compliance.getCatalogReference())
                    || compliance.getCatalogReference().isBlank()
                    || compliance.getGrades().isEmpty() || compliance.getAllergens().isEmpty()
                    || environment.getProperty("wx.appid", "").isBlank()
                    || environment.getProperty("wx.secret", "").isBlank()) {
                throw new IllegalStateException("prod collection requires approved catalogs and WeChat credentials");
            }
        }
    }

    private boolean isDemonstration(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("synthetic") || normalized.contains("test-secret")
                || normalized.contains("dev-secret") || normalized.contains("demo");
    }
}
