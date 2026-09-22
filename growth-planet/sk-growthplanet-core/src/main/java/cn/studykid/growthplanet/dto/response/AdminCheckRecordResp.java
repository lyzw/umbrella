package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 打卡记录运营视图行（M4，life_check_record 只读，item_name 为写入时快照）。孩子姓名默认脱敏。 */
@Data
@Builder
public class AdminCheckRecordResp {
    private Long id;
    private Long familyId;
    private Long childId;
    private String childName;
    private Long itemId;
    private String itemName;
    private LocalDate checkDate;
    private LocalDateTime checkTime;
}
