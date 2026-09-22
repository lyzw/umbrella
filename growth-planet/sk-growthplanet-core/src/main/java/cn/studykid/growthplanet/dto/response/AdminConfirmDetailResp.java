package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 确认单运营详情（M4）：单头 + 明细行快照。孩子姓名默认脱敏。 */
@Data
@Builder
public class AdminConfirmDetailResp {
    private Long id;
    private String confirmNo;
    private Long familyId;
    private Long childId;
    private String childName;
    private LocalDate menuDate;
    private String mealType;
    private BigDecimal totalAmount;
    private String status;
    private Boolean isOverLimit;
    private Integer version;
    private BigDecimal estimatedBalance;
    private BigDecimal completedBalance;
    private String remark;
    private LocalDateTime submitTime;
    private List<Item> items;

    /** 明细行（life_menu_item 快照）。 */
    @Data
    @Builder
    public static class Item {
        private Long dishId;
        private String sourceType;
        private String dishName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
        private String note;
    }
}
