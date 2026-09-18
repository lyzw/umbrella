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
 * 逻辑删除后普通查询不可见，但原身份仍占用唯一键，不可重建。
 */
class LogicDeleteTest extends BaseIT {

    @Autowired
    private UserMapper userMapper;

    @Test
    void logicDeleteDoesNotReleaseIdentity() {
        String openid = "openid_logic_" + UUID.randomUUID();

        User user = new User();
        user.setOpenid(openid);
        user.setRole("UNSELECTED");
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

        // 逻辑删除不释放身份，重复插入必须被唯一索引拒绝。
        User rebuilt = new User();
        rebuilt.setOpenid(openid);
        rebuilt.setRole("UNSELECTED");
        rebuilt.setStatus("NORMAL");
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> userMapper.insert(rebuilt));
    }
}
