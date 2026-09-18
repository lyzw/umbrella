package com.growthplanet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * JWT 相关配置属性（前缀 jwt）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** HS256 密钥，长度需 >= 32 字节（256 bit）。生产环境请放入配置中心。 */
    private String secret = "growth-planet-default-secret-key-please-change-in-prod-0123456789";

    /** access token 有效期（毫秒）。默认 24h。 */
    private long accessTtl = 86_400_000L;

    /** 签发方标识。 */
    private String issuer = "growth-planet";
}
