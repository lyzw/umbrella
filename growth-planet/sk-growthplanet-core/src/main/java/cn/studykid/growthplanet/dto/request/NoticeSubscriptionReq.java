package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class NoticeSubscriptionReq {
    @NotNull @Positive
    private Long noticeId;
    @NotNull
    private Boolean accepted;
}
