package cn.studykid.growthplanet.config;

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
    private String secret;

    /** access token 有效期（毫秒）。默认 30 分钟。 */
    private long accessTtl = 1_800_000L;

    /** 签发方标识。 */
    private String issuer = "growth-planet";

    /** 后台运营端独立密钥（与 C 端隔离，避免 token 互解）。长度需 >= 32 字节。 */
    private String adminSecret;

    /** 后台 access token 有效期（毫秒）。默认 30 分钟。 */
    private long adminAccessTtl = 1_800_000L;

    /** 后台签发方标识。 */
    private String adminIssuer = "growth-planet-admin";
}
