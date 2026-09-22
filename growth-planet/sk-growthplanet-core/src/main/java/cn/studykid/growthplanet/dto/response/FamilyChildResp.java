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
public class FamilyChildResp {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long familyId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long childId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long applyId;
    private String bindStatus;
    private Integer applicationVersion;
    private String relationLabel;
}
