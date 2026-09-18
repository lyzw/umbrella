package com.growthplanet;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.growthplanet.entity.User;
import com.growthplanet.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 逻辑删除（delete_at 时间戳）重建语义测试：删除后正常查询不可见，
 * 因复合唯一键 UNIQUE(openid, delete_at)，可凭相同 openid 重建。
 */
class LogicDeleteTest extends BaseIT {

    @Autowired
    private UserMapper userMapper;

    @Test
    void logicDeleteAllowsRebuild() {
        String openid = "openid_logic_" + UUID.randomUUID();

        User user = new User();
        user.setOpenid(openid);
        user.setRole("UNSET");
        user.setStatus("NORMAL");
        userMapper.insert(user);
        Long id = user.getId();
        assertNotNull(id);

        // 逻辑删除：注入 UNIX_TIMESTAMP()*1000（避免 MyBatis-Plus 对函数取值加引号）
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("id", id)
                .eq("delete_at", 0)
                .setSql("delete_at = UNIX_TIMESTAMP() * 1000"));

        // 正常查询（自动追加 delete_at = 0）应不可见
        User found = userMapper.selectOne(new QueryWrapper<User>().eq("openid", openid));
        assertNull(found, "逻辑删除后不应被正常查询命中");

        // 重建：相同 openid 可再次插入（复合唯一键 delete_at 不同）
        User rebuilt = new User();
        rebuilt.setOpenid(openid);
        rebuilt.setRole("UNSET");
        rebuilt.setStatus("NORMAL");
        userMapper.insert(rebuilt);
        assertNotNull(rebuilt.getId());

        User rebuiltFound = userMapper.selectOne(new QueryWrapper<User>().eq("openid", openid));
        assertNotNull(rebuiltFound, "重建后相同 openid 应可查询到新记录");
    }
}
