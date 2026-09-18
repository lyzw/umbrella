package cn.studykid.growthplanet.util;

import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class BusinessRequest {
    private BusinessRequest() {
    }

    public static void requireKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "Idempotency-Key 必填，最长64字符");
        }
    }

    public static String hash(Object canonicalValue) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJson(canonicalValue).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public static void page(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
    }

    public static String limit(int page, int pageSize) {
        page(page, pageSize);
        return "LIMIT " + pageSize + " OFFSET " + (((long) page - 1) * pageSize);
    }

    public static String money(BigDecimal value) {
        return value.setScale(2).toPlainString();
    }
}
