package cn.studykid.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 批量发布结果：逐条返回成败与业务错误码，允许部分成功。
 * {@code code} 为 {@code E-xxx} 业务码（成功时为 null），前端据此提示"哪一条要重试"。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuBatchResp {
    private int okCount;
    private int failCount;
    private List<Result> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private LocalDate menuDate;
        private String mealType;
        private boolean ok;
        private String code;
    }
}
