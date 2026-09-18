package cn.studykid.growthplanet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChildPreferencesResp {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long childId;
    private List<String> dislikes;
    private List<String> tastes;
}
