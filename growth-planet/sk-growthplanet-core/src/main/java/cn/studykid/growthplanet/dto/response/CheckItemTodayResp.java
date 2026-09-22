package cn.studykid.growthplanet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 今日某打卡项已打卡次数与上限，驱动「已达上限」展示（F-034）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckItemTodayResp {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long itemId;
    private String itemName;
    private int count;
    private int dailyTarget;
    private boolean reached;
}
