package com.growthplanet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.growthplanet.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统审计日志表 Mapper。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
