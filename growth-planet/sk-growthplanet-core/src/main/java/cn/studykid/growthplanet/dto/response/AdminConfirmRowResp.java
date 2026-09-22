package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 确认单运营列表行（M4）。孩子姓名默认脱敏。 */
@Data
@Builder
public class AdminConfirmRowResp {
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
    private LocalDateTime submitTime;
}
