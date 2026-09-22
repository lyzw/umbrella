package cn.studykid.growthplanet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DishCategoryResp {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long categoryId;
    private String name;
    private Integer sort;
    private String status;
}
