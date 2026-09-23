package cn.studykid.growthplanet.controller.console;

import cn.studykid.growthplanet.common.constant.AdminResource;
import cn.studykid.growthplanet.common.context.AdminUserContext;
import cn.studykid.growthplanet.common.enums.AdminAction;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.request.AdminUploadTokenReq;
import cn.studykid.growthplanet.dto.response.UploadTokenResp;
import cn.studykid.growthplanet.service.ObjectStorageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对象存储接口（七牛云 Kodo）：为运营端提供客户端直传凭证。
 * 前缀 {@code /api/admin/**}，权限点「对象存储:新建」。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminStorageController {

    private final ObjectStorageService storageService;

    public AdminStorageController(ObjectStorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * 申请客户端直传凭证。后端不接触文件字节，仅签发限定 key 的短时效 token。
     */
    @PostMapping("/storage/upload-token")
    public Result<UploadTokenResp> uploadToken(@Valid @RequestBody AdminUploadTokenReq req) {
        AdminUserContext.requirePerm(AdminResource.STORAGE, AdminAction.CREATE.code());
        return Result.ok(storageService.genUploadToken(req.getBizType(), req.getFileName()));
    }
}
