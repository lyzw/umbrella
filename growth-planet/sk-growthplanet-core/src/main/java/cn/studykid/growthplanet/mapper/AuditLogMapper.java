package cn.studykid.growthplanet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.studykid.growthplanet.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统审计日志表 Mapper。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
