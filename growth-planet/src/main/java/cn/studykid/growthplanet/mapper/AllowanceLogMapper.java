package cn.studykid.growthplanet.mapper;

import cn.studykid.growthplanet.entity.AllowanceLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AllowanceLogMapper extends BaseMapper<AllowanceLog> {
    @Select("SELECT * FROM life_allowance_log WHERE operator_id=#{operatorId} AND scene='MANUAL' "
            + "AND request_key=#{key} FOR UPDATE")
    AllowanceLog findGrant(Long operatorId, String key);

    // 审计流水不可删除；累计包含逻辑删除标记，防止隐藏流水绕过额度。
    @Select("SELECT COALESCE(SUM(amount),0) FROM life_allowance_log WHERE child_id=#{childId} "
            + "AND trans_type='DEDUCT' AND usage_date BETWEEN #{start} AND #{end}")
    BigDecimal sumUsage(Long childId, LocalDate start, LocalDate end);

    // 看板（F-024）：按流水类型在日期区间内求和，供家长端本周支出（DEDUCT）/本周发放（GRANT）使用。
    @Select("SELECT COALESCE(SUM(amount),0) FROM life_allowance_log WHERE child_id=#{childId} "
            + "AND trans_type=#{transType} AND usage_date BETWEEN #{start} AND #{end}")
    BigDecimal sumByType(Long childId, String transType, LocalDate start, LocalDate end);
}
