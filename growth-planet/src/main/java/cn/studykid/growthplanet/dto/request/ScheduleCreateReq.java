package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 家长创建日程/提醒（F-036）。cross-field 规则（每周须带星期、日期不早于今天）在 Service 校验。 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ScheduleCreateReq extends StrictRequest {
    @NotNull
    @Positive
    private Long childId;
    @NotBlank
    @Size(max = 64)
    private String title;
    @NotBlank
    @Pattern(regexp = "HOMEWORK|CLASS|MEDICINE|OTHER")
    private String category;
    @Size(max = 255)
    private String note;
    /** 日程发生起始日，yyyy-MM-dd。 */
    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
    private String scheduleDate;
    /** 日程发生/提醒时刻，HH:mm（24 小时制）。 */
    @NotBlank
    @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d")
    private String scheduleTime;
    @NotBlank
    @Pattern(regexp = "ONCE|DAILY|WEEKLY")
    private String repeatType;
    /** 每周重复的星期，形如 "1,3,5"（周一=1 ~ 周日=7）；仅 WEEKLY 时必填。 */
    @Pattern(regexp = "^$|[1-7](,[1-7]){0,6}")
    private String repeatWeekdays;
    /** 提前提醒分钟数，0=准点（默认），上限 1440。 */
    @Min(0)
    @Max(1440)
    private Integer remindMinutes;
}
