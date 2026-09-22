package cn.studykid.growthplanet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.studykid.growthplanet.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM usr_user WHERE openid = #{openid}")
    User findIdentityIncludingDeleted(String openid);
}
