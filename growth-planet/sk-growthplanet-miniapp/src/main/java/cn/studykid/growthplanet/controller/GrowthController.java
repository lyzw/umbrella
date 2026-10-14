package cn.studykid.growthplanet.controller;

import cn.studykid.growthplanet.common.annotation.RequireRole;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.request.GrowthRecordReq;
import cn.studykid.growthplanet.dto.response.GrowthRecordItem;
import cn.studykid.growthplanet.dto.response.GrowthRecordsResp;
import cn.studykid.growthplanet.dto.response.GrowthSaveResp;
import cn.studykid.growthplanet.dto.response.GrowthStandardResp;
import cn.studykid.growthplanet.service.GrowthRecordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 儿童成长记录接口：家长录入 / 本人与家长查询 / WHO 标准分位表。
 */
@RestController
@RequestMapping("/api/mini/growth")
public class GrowthController {

    private final GrowthRecordService growthRecordService;

    public GrowthController(GrowthRecordService growthRecordService) {
        this.growthRecordService = growthRecordService;
    }

    @PostMapping("/records")
    @RequireRole(RoleEnum.PARENT)
    public Result<GrowthSaveResp> saveRecord(@RequestBody @Valid GrowthRecordReq req) {
        return Result.ok(growthRecordService.save(req));
    }

    @GetMapping("/records")
    @RequireRole({RoleEnum.PARENT, RoleEnum.CHILD})
    public Result<GrowthRecordsResp> listRecords(
            @RequestParam @Positive Long childId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) {
        return Result.ok(growthRecordService.list(childId, limit));
    }

    @GetMapping("/records/latest")
    @RequireRole({RoleEnum.PARENT, RoleEnum.CHILD})
    public Result<GrowthRecordItem> latestRecord(@RequestParam @Positive Long childId) {
        GrowthRecordItem latest = growthRecordService.latest(childId);
        return latest == null ? Result.ok(null) : Result.ok(latest);
    }

    @GetMapping("/standard")
    public Result<GrowthStandardResp> standard(
            @RequestParam(defaultValue = "WHO 2007") @Pattern(regexp = "WHO 2006|WHO 2007",
                    message = "年龄段仅支持 WHO 2006 / WHO 2007") String ageBand,
            @RequestParam @Pattern(regexp = "BOY|GIRL", message = "性别仅支持 BOY/GIRL") String gender,
            @RequestParam @Pattern(regexp = "HEIGHT|WEIGHT|BMI", message = "指标仅支持 HEIGHT/WEIGHT/BMI") String metric) {
        return Result.ok(growthRecordService.standard(ageBand, gender, metric));
    }
}
