package cn.studykid.growthplanet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckItemResp {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long itemId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long familyId;
    private String name;
    private String icon;
    private String unit;
    private Integer dailyTarget;
    private Integer sortOrder;
    private Integer version;
}
