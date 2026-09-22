package cn.studykid.growthplanet.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class DishReq {
    @NotNull @Positive
    private Long categoryId;
    @NotBlank @Size(max = 64)
    private String name;
    @Size(max = 255)
    private String imageUrl;
    @NotNull @DecimalMin("0.00") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2)
    private BigDecimal virtualPrice;
    @PositiveOrZero
    private Integer calories;
    @Size(max = 255)
    private String tags;
    @NotNull @Size(max = 20)
    private List<@NotBlank @Size(max = 64) String> allergens;
    @NotNull @Pattern(regexp = "UNKNOWN|DECLARED")
    private String allergenStatus;
    @NotNull @Min(0) @Max(3)
    private Integer spiceLevel;
    @NotNull @Pattern(regexp = "ON_SALE|OFF_SALE")
    private String status;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported dish field");
    }
}
