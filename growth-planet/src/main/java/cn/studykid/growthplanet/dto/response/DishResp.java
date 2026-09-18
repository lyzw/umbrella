package cn.studykid.growthplanet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DishResp {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long dishId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long categoryId;
    private String name;
    private String imageUrl;
    private String virtualPrice;
    private Integer calories;
    private String tags;
    private List<String> allergens;
    private String allergenStatus;
    private Integer spiceLevel;
    private String status;
}
