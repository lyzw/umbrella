package cn.studykid.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 儿童提醒家长发布今日家庭餐单的结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuReminderResp {
    private String status;
}
