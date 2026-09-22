package cn.studykid.growthplanet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 心愿菜单详情（P3）。
 * <p>
 * status：NONE（当天未创建，可选功能）/ SUBMITTED（已提交且当日锁定）/ WITHDRAWN（已撤回可改选）。
 * items 为该 (childId, menuDate) 下全部想吃标记按 (type,id) 去重后的候选池；
 * submitted 标记该菜是否被"上一次提交"收录（撤回后仍保留，便于回显）。
 */
@Data
@Builder
public class WishMenuResp {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long menuId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long childId;
    private LocalDate menuDate;
    private LocalDate today;
    private String status;
    private boolean enabled;
    private int maxDishes;
    /** 候选池道数（去重后的标记数）。 */
    private int dishCount;
    /** 上次提交收录的道数。 */
    private int submittedCount;
    private boolean canSubmit;
    private boolean canEdit;
    /** 已提交且未撤回 → 当日锁定（禁止标记增删与重复提交）。 */
    private boolean locked;
    /** 提交/撤回用的乐观锁版本（无提交单时为 0）。 */
    private int version;
    private LocalDateTime submitTime;
    private List<Item> items;

    @Data
    @Builder
    public static class Item {
        private String type;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long id;
        private String name;
        private String imageUrl;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long categoryId;
        private String categoryName;
        private Integer spiceLevel;
        /** 该菜在该日出现在哪些餐次的标记里（心愿目录标记为 ALL）。 */
        private List<String> mealTypes;
        private boolean submitted;
        /** 菜品已下架/删除：占位不丢行。 */
        private boolean missing;
        /** 上架状态（missing 时为 null），供前端统一安全提示 safetyLabel 使用。 */
        private String status;
        private String safetyStatus;
        private boolean allergyConflict;
        private boolean disliked;
        private boolean selectable;
    }
}
