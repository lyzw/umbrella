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
public class CheckRecordResp {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long recordId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long itemId;
    private String itemName;
    private String checkDate;
    private String checkTime;
}
