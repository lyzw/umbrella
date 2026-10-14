package cn.studykid.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 儿童成长记录列表（按 record_date DESC，最新在前）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrowthRecordsResp {
    private Long childId;
    private List<GrowthRecordItem> items;
}
