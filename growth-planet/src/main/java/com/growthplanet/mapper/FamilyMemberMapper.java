package com.growthplanet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.growthplanet.entity.FamilyMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 家庭成员关系表 Mapper。
 */
@Mapper
public interface FamilyMemberMapper extends BaseMapper<FamilyMember> {
}
