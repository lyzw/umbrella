package cn.studykid.growthplanet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 打卡日历与连续天数（F-035）。checkedDates 为 yyyy-MM-dd 列表，按月可选过滤。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckCalendarResp {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long childId;
    private int currentStreak;
    private List<String> checkedDates;
}
