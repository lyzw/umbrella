package cn.studykid.growthplanet.mapper;

import cn.studykid.growthplanet.entity.MenuConfirm;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MenuConfirmMapper extends BaseMapper<MenuConfirm> {
    @Select("SELECT * FROM life_menu_confirm WHERE child_id=#{childId} AND request_key=#{key} FOR UPDATE")
    MenuConfirm findRequest(Long childId, String key);
}
