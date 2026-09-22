package cn.studykid.growthplanet.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 心愿菜单提交（P3）。
 * refs 为孩子勾选的菜（DishRef 双键）；数量上限由家长设置决定，服务端在业务层强校验；
 * expectedVersion 为 {@code life_wish_menu.version}（无提交单时传 0），防止重复提交产生重复通知。
 */
@Data
public class WishSubmitReq {
    @NotNull
    private LocalDate menuDate;
    @NotNull @Size(min = 1, max = 10)
    private List<Ref> refs;
    @NotNull
    private Integer expectedVersion;

    @Data
    public static class Ref {
        @NotNull
        private String type;
        @NotNull @Positive
        private Long id;
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported wish submit field");
    }
}
