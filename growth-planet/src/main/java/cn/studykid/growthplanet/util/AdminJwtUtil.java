package cn.studykid.growthplanet.util;

import cn.studykid.growthplanet.common.context.LoginAdmin;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * 后台运营端 JWT 工具：使用独立密钥/issuer（growth-planet-admin），与 C 端 token 互不可解。
 * Claims：aid / role / role_id / token_version / jti / issuer。
 */
@Component
public class AdminJwtUtil {

    private final String secret;
    private final long accessTtl;
    private final String issuer;
    private final SecretKey key;

    @org.springframework.beans.factory.annotation.Autowired
    public AdminJwtUtil(JwtProperties props) {
        this(props.getAdminSecret(), props.getAdminAccessTtl(), props.getAdminIssuer());
    }

    /** 供单元测试直接构造。 */
    public AdminJwtUtil(String secret, long accessTtl, String issuer) {
        this.secret = secret;
        this.accessTtl = accessTtl;
        this.issuer = issuer;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(LoginAdmin admin) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(admin.getAdminId()))
                .claim("aid", admin.getAdminId())
                .claim("role", admin.getRoleCode())
                .claim("role_id", admin.getRoleId())
                .claim("token_version", admin.getTokenVersion() == null ? 0L : admin.getTokenVersion())
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTtl))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "后台 token 解析失败");
        }
    }

    public Long getAdminId(Claims claims) {
        return claims.get("aid", Long.class);
    }

    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public Long getRoleId(Claims claims) {
        return claims.get("role_id", Long.class);
    }

    public Long getTokenVersion(Claims claims) {
        return claims.get("token_version", Long.class);
    }
}
