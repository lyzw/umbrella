package com.growthplanet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.growthplanet.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
