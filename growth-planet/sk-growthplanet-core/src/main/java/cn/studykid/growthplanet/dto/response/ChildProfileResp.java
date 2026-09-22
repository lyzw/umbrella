package cn.studykid.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 儿童档案提交响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChildProfileResp {
    private String profileStatus;
}
