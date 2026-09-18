package cn.studykid.growthplanet.common.exception;

import cn.studykid.growthplanet.dto.response.AllowancePreviewResp;
import lombok.Getter;

@Getter
public class AllowanceConfirmationException extends RuntimeException {
    private final AllowancePreviewResp preview;

    public AllowanceConfirmationException(AllowancePreviewResp preview) {
        super("Allowance confirmation required");
        this.preview = preview;
    }
}
