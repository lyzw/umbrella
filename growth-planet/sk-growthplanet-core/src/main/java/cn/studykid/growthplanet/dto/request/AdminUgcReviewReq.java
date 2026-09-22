package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 运营端 UGC 审核动作请求（M3 审核队列）：通过（visibility→PUBLIC）/驳回（写 reject_reason）。 */
@Data
public class AdminUgcReviewReq {

    @NotBlank
    @Pattern(regexp = "APPROVE|REJECT")
    private String action;

    /** 驳回时必填（≤255 字），通过时忽略 */
    @Size(max = 255)
    private String reason;

    /** 手写乐观锁：客户端读取行时拿到的 version */
    @NotNull
    @PositiveOrZero
    private Integer expectedVersion;
}
