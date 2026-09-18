package com.growthplanet;

import com.growthplanet.util.InviteCodeUtil;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InviteCodeUtil 单元测试（纯逻辑）。
 */
class InviteCodeUtilTest {

    @Test
    void generatedCodeMatchesFormat() {
        for (int i = 0; i < 100; i++) {
            String code = InviteCodeUtil.randomCode();
            assertTrue(InviteCodeUtil.isValidFormat(code),
                    "生成码不符合 [A-Z0-9]{6}：" + code);
            assertEquals(6, code.length());
        }
    }

    @Test
    void generatedCodesAreUniqueInBatch() {
        int n = 2000;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < n; i++) {
            seen.add(InviteCodeUtil.randomCode());
        }
        // 36^6 组合空间极大，2000 个码碰撞概率可忽略；此处验证生成器本身不产生重复
        assertEquals(n, seen.size(), "批量生成出现重复码，去重逻辑需关注");
    }
}
