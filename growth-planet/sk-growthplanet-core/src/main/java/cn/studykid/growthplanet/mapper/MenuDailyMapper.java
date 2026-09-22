package cn.studykid.growthplanet.mapper;

import cn.studykid.growthplanet.entity.MenuDaily;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MenuDailyMapper extends BaseMapper<MenuDaily> {
    // The unique owner/date/meal key serializes concurrent first publication without replacing the row.
    @Insert("""
            INSERT INTO life_menu_daily
              (source_type, owner_key, school, family_id, menu_date, meal_type, dish_ids, status)
            VALUES (#{sourceType}, #{ownerKey}, #{school}, #{familyId}, #{menuDate}, #{mealType},
              #{dishIds,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler}, #{status})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertOrKeep(MenuDaily menu);
}
