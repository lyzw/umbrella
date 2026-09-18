package cn.studykid.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 加入家庭响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinFamilyResp {
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private Long applyId;
    private String status;
}
