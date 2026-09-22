package cn.studykid.growthplanet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.studykid.growthplanet.entity.ConsentLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 监护人同意书记录表 Mapper。
 */
@Mapper
public interface ConsentLogMapper extends BaseMapper<ConsentLog> {
}
