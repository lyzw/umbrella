package cn.studykid.growthplanet;

import cn.studykid.growthplanet.common.context.LoginUser;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.util.JwtUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JwtUtil 单元测试（纯逻辑，无 Spring 上下文 / 数据库）。
 */
class JwtUtilTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long-0123456789";
    private static final long TTL = 3_600_000L;

    private JwtUtil newUtil() {
        return new JwtUtil(SECRET, TTL, "growth-planet");
    }

    @Test
    void issueAndParseRoundTrip() {
        JwtUtil util = newUtil();
        LoginUser user = LoginUser.builder()
                .userId(123L)
                .role("PARENT")
                .familyIds(List.of(10L, 20L))
                .jti("jt-1")
                .build();
        String token = util.generateToken(user);

        assertNotNull(token);
        var claims = util.parse(token);
        assertEquals(123L, util.getUserId(claims));
        assertEquals("PARENT", util.getRole(claims));
        assertEquals(List.of(10L, 20L), util.getFamilyIds(claims));
        assertEquals("jt-1", util.getJti(claims));
    }

    @Test
    void expiredTokenThrowsE001() {
        JwtUtil util = newUtil();
        LoginUser user = LoginUser.builder().userId(1L).role("CHILD").familyIds(List.of()).jti("x").build();
        // 已过期
        String token = util.generateToken(user, -1000L);
        BizException ex = assertThrows(BizException.class, () -> util.parse(token));
        assertEquals(ResultCode.E001_NO_WX_AUTH, ex.getResultCode());
    }

    @Test
    void tamperedTokenThrowsE001() {
        JwtUtil util = newUtil();
        LoginUser user = LoginUser.builder().userId(1L).role("CHILD").familyIds(List.of()).jti("x").build();
        String token = util.generateToken(user);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "B" : "A");
        BizException ex = assertThrows(BizException.class, () -> util.parse(tampered));
        assertEquals(ResultCode.E001_NO_WX_AUTH, ex.getResultCode());
    }

    @Test
    void invalidSecretFailsVerification() {
        JwtUtil issuer = newUtil();
        JwtUtil other = new JwtUtil("different-secret-key-also-at-least-32-bytes-ok", TTL, "growth-planet");
        LoginUser user = LoginUser.builder().userId(1L).role("CHILD").familyIds(List.of()).jti("x").build();
        String token = issuer.generateToken(user);
        BizException ex = assertThrows(BizException.class, () -> other.parse(token));
        assertEquals(ResultCode.E001_NO_WX_AUTH, ex.getResultCode());
    }
}
