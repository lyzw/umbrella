package cn.studykid.growthplanet.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 运营看板聚合查询（实时聚合，无汇总表）。
 * 所有查询统一排除软删 {@code delete_at = 0}；聚合口径见《运营后台_里程碑A实施规划》M1。
 */
@Mapper
public interface AdminStatsMapper {

    /** 想吃热度 Top（按 dish_type+dish_id 聚合，默认近 30 天） */
    @Select("""
            SELECT dish_type, dish_id, COUNT(*) AS cnt
            FROM usr_child_want_eat
            WHERE delete_at = 0 AND create_time >= #{rangeStart}
            GROUP BY dish_type, dish_id
            ORDER BY cnt DESC
            LIMIT 10
            """)
    List<Map<String, Object>> wantEatTop(@Param("rangeStart") LocalDateTime rangeStart);

    /**
     * 确认单来源占比（FAMILY vs SCHOOL）。
     * 来源类型记录在 life_menu_daily.source_type，确认单通过 menu_id 关联每日菜单，
     * 故需 JOIN life_menu_daily 取 source_type 后按来源聚合。
     */
    @Select("""
            SELECT d.source_type AS source_type, COUNT(*) AS cnt
            FROM life_menu_confirm c
            JOIN life_menu_daily d ON d.id = c.menu_id AND d.delete_at = 0
            WHERE c.delete_at = 0
            GROUP BY d.source_type
            """)
    List<Map<String, Object>> confirmSourceRatio();

    /** 零花钱收发汇总（按 trans_type 汇总金额，默认近 30 天） */
    @Select("""
            SELECT trans_type, COALESCE(SUM(amount), 0) AS total
            FROM life_allowance_log
            WHERE delete_at = 0 AND create_time >= #{rangeStart}
            GROUP BY trans_type
            """)
    List<Map<String, Object>> allowanceSumByType(@Param("rangeStart") LocalDateTime rangeStart);

    /** 钱包沉淀总额（余额合计） */
    @Select("""
            SELECT COALESCE(SUM(balance), 0) AS total
            FROM life_wallet
            WHERE delete_at = 0
            """)
    Long walletBalanceTotal();

    /** 连续打卡天数分布（按 current_streak 分桶） */
    @Select("""
            SELECT CASE
                     WHEN current_streak = 0 THEN '0'
                     WHEN current_streak BETWEEN 1 AND 2 THEN '1-2'
                     WHEN current_streak BETWEEN 3 AND 6 THEN '3-6'
                     WHEN current_streak BETWEEN 7 AND 13 THEN '7-13'
                     ELSE '14+'
                   END AS bucket,
                   COUNT(*) AS cnt
            FROM life_chore_streak
            WHERE delete_at = 0
            GROUP BY bucket
            ORDER BY bucket
            """)
    List<Map<String, Object>> choreStreakDistribution();

    /** 热门勋章 Top（按 definition_id 聚合发放量，带名称） */
    @Select("""
            SELECT a.definition_id AS definition_id, d.name AS name, COUNT(*) AS cnt
            FROM life_medal_award a
            LEFT JOIN life_medal_definition d ON d.id = a.definition_id AND d.delete_at = 0
            WHERE a.delete_at = 0
            GROUP BY a.definition_id, d.name
            ORDER BY cnt DESC
            LIMIT 10
            """)
    List<Map<String, Object>> medalTop();

    /** 近 N 日活跃家庭数（确认单/家务/打卡任一发生即视为活跃） */
    @Select("""
            SELECT COUNT(DISTINCT family_id) AS cnt FROM (
              SELECT family_id FROM life_menu_confirm WHERE delete_at = 0 AND create_time >= #{since}
              UNION
              SELECT family_id FROM life_chore_instance WHERE delete_at = 0 AND create_time >= #{since}
              UNION
              SELECT family_id FROM life_check_record WHERE delete_at = 0 AND create_time >= #{since}
            ) t
            """)
    Long activeFamilies(@Param("since") LocalDateTime since);

    /** 近 N 日活跃孩子数 */
    @Select("""
            SELECT COUNT(DISTINCT child_id) AS cnt FROM (
              SELECT child_id FROM life_menu_confirm WHERE delete_at = 0 AND create_time >= #{since}
              UNION
              SELECT child_id FROM life_chore_instance WHERE delete_at = 0 AND create_time >= #{since}
              UNION
              SELECT child_id FROM life_check_record WHERE delete_at = 0 AND create_time >= #{since}
            ) t
            """)
    Long activeChildren(@Param("since") LocalDateTime since);

    /** 近 N 日打卡覆盖孩子数（去重） */
    @Select("""
            SELECT COUNT(DISTINCT child_id) AS cnt
            FROM life_check_record
            WHERE delete_at = 0 AND create_time >= #{since}
            """)
    Long checkCoveredChildren(@Param("since") LocalDateTime since);

    /** 勋章获得孩子数（去重） */
    @Select("""
            SELECT COUNT(DISTINCT child_id) AS cnt
            FROM life_medal_award
            WHERE delete_at = 0
            """)
    Long medalAwardDistinctChildren();
}
