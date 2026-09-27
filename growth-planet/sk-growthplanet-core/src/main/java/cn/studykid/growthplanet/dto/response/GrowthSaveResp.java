package cn.studykid.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新增成长记录响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrowthSaveResp {
    private Long id;
}
