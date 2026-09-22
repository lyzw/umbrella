package cn.studykid.growthplanet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 家长按区间查看孩子的心愿菜单提交记录（P3）。
 * 过期（menuDate &lt; today）照常返回并标 expired —— 与想吃看板"不丢行"原则一致。
 */
@Data
@Builder
public class WishMenuListResp {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long childId;
    private LocalDate from;
    private LocalDate to;
    private LocalDate today;
    private List<Entry> items;

    @Data
    @Builder
    public static class Entry {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long menuId;
        private LocalDate menuDate;
        private String status;
        private int dishCount;
        private int maxDishes;
        private LocalDateTime submitTime;
        private boolean expired;
        private List<WishMenuResp.Item> items;
    }
}
