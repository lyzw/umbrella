package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 每日想吃运营视图行（M4）。孩子姓名默认脱敏（张*）。 */
@Data
@Builder
public class AdminWantEatResp {
    private Long id;
    private Long childId;
    private String childName;
    private Long familyId;
    private LocalDate menuDate;
    private String mealType;
    private String sourceType;
    private String dishType;
    private Long dishId;
    private String dishName;
    private String status;
    private Long wishId;
    private LocalDateTime createTime;
}
