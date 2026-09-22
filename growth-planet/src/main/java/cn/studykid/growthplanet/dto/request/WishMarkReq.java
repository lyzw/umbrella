package cn.studykid.growthplanet.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;

/**
 * 心愿菜单候选池标记/移除（P3）。
 * type 必填（PRESET/FAMILY）：两表独立自增会撞号，必须与 id 组成 DishRef 双键；
 * 家庭归属由后端从儿童鉴权派生，客户端不得传入 familyId / sourceType。
 */
@Data
public class WishMarkReq {
    @NotNull
    private LocalDate menuDate;
    /** PRESET / FAMILY。 */
    @NotNull
    private String type;
    @NotNull @Positive
    private Long id;
    /** true=加入候选池；false=移出候选池。 */
    @NotNull
    private Boolean selected;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported wish mark field");
    }
}
