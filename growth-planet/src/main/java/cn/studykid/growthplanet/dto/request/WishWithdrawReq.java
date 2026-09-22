package cn.studykid.growthplanet.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 心愿菜单撤回（P3）：SUBMITTED → WITHDRAWN，解除当日锁定。
 * 撤回保留明细的 wish_id，便于前端回显"上次提交了哪些"。
 */
@Data
public class WishWithdrawReq {
    @NotNull
    private LocalDate menuDate;
    @NotNull
    private Integer expectedVersion;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported wish withdraw field");
    }
}
