package com.growthplanet.util;

import com.growthplanet.config.JwtProperties;
import com.growthplanet.common.context.LoginUser;
import com.growthplanet.common.exception.BizException;
import com.growthplanet.common.result.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JWT 工具：签发 / 解析 / 校验（jjwt 0.12.x，HS256）。
 * Claims：user_id / role / family_ids / jti / exp。
 */
@Component
public class JwtUtil {

    private final String secret;
    private final long accessTtl;
    private final String issuer;
    private final SecretKey key;

    @org.springframework.beans.factory.annotation.Autowired
    public JwtUtil(JwtProperties props) {
        this(props.getSecret(), props.getAccessTtl(), props.getIssuer());
    }

    /** 供单元测试直接构造（不依赖 Spring 上下文）。 */
    public JwtUtil(String secret, long accessTtl, String issuer) {
        this.secret = secret;
        this.accessTtl = accessTtl;
        this.issuer = issuer;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 基于登录用户签发 token；若 user 已显式设置 jti 则沿用，否则生成新 jti。 */
    public String generateToken(LoginUser user) {
        return issue(user, accessTtl);
    }

    /** 基于登录用户签发 token，指定有效期（用于测试过期场景）。沿用 user 的 jti（若有）。 */
    public String generateToken(LoginUser user, long ttlMillis) {
        return issue(user, ttlMillis);
    }

    public String generateToken(Long userId, String role, List<Long> familyIds, long ttlMillis) {
        return generateToken(userId, role, familyIds, ttlMillis, null);
    }

    /**
     * 底层签发方法。jti 为 null 或空时自动生成 UUID；否则沿用传入值（用于测试与特定场景）。
     */
    public String generateToken(Long userId, String role, List<Long> familyIds, long ttlMillis, String jti) {
        return issue(new LoginUser(userId, role, familyIds, jti), ttlMillis);
    }

    public long getExpiresIn() {
        return accessTtl / 1000;
    }

    private String issue(LoginUser user, long ttlMillis) {
        long now = System.currentTimeMillis();
        String jti = user.getJti();
        String effectiveJti = (jti != null && !jti.isEmpty()) ? jti : UUID.randomUUID().toString();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getUserId()))
                .claim("user_id", user.getUserId())
                .claim("role", user.getRole())
                .claim("family_ids", user.getFamilyIds() == null ? List.of() : user.getFamilyIds())
                .claim("token_version", user.getTokenVersion())
                .claim("jti", effectiveJti)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 解析并校验签名/过期，失败抛 E-001。 */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "token 解析失败");
        }
    }

    public Long getUserId(Claims claims) {
        return claims.get("user_id", Long.class);
    }

    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }

    /** family_ids 反序列化可能为 Integer（小数字），统一转 Long。 */
    public List<Long> getFamilyIds(Claims claims) {
        Object raw = claims.get("family_ids");
        if (raw == null) {
            return List.of();
        }
        List<?> list = (List<?>) raw;
        return list.stream()
                .map(o -> o == null ? null : ((Number) o).longValue())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public String getJti(Claims claims) {
        return claims.get("jti", String.class);
    }

    /** token 剩余有效期（毫秒），用于黑名单 TTL。 */
    public long getRemainingTtl(Claims claims) {
        Date exp = claims.getExpiration();
        if (exp == null) {
            return 0L;
        }
        return Math.max(0L, exp.getTime() - System.currentTimeMillis());
    }
}
