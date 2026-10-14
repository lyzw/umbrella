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
public class ChildProfileDetailResp {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long childId;
    private String nickname;
    private String grade;
    private String school;
    private Integer birthYear;
    private Integer birthMonth;
    /** 当前周岁（由出生年月计算；未填写时为空）。 */
    private Integer ageYears;
    private List<String> allergies;
    private List<String> dislikes;
    private List<String> tastes;
    private String profileStatus;
}
