package cn.studykid.growthplanet;

import cn.studykid.growthplanet.config.ComplianceProperties;
import cn.studykid.growthplanet.config.JwtProperties;
import cn.studykid.growthplanet.config.ProductionConfigGuard;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionConfigGuardTest {
    @Test
    void productionRejectsMixedProfilesAndDemonstrationSecrets() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("prod", "dev");
        var jwt = new JwtProperties();
        jwt.setSecret("synthetic-only-development-secret-0123456789");
        var guard = new ProductionConfigGuard(environment, jwt, new ComplianceProperties());
        assertThrows(IllegalStateException.class, guard::validate);
        environment.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void productionMayStartClosedButCannotEnableSyntheticCollection() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        var jwt = new JwtProperties();
        jwt.setSecret("fixture-random-key-012345678901234567890123456789");
        // 运营后台引入独立 admin 账号体系后，prod 守卫同时要求独立 admin 密钥（30 分钟 TTL 为默认值）。
        jwt.setAdminSecret("fixture-random-admin-key-012345678901234567890123456789");
        var policy = new ComplianceProperties();
        var guard = new ProductionConfigGuard(environment, jwt, policy);
        assertDoesNotThrow(guard::validate);
        policy.setCollectionEnabled(true);
        policy.setApprovalReference("SYNTHETIC-DEMO-ONLY");
        policy.setAgreementText("synthetic agreement");
        assertThrows(IllegalStateException.class, guard::validate);
        policy.setCollectionEnabled(false);
        jwt.setAccessTtl(86_400_000L);
        assertThrows(IllegalStateException.class, guard::validate);
    }
}
