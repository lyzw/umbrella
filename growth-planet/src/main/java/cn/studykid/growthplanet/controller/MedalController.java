package cn.studykid.growthplanet.controller;

import cn.studykid.growthplanet.common.annotation.RequireRole;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.response.ChildMedalResp;
import cn.studykid.growthplanet.dto.response.MedalDefinitionResp;
import cn.studykid.growthplanet.service.MedalService;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/medal")
public class MedalController {
    private final MedalService service;

    public MedalController(MedalService service) {
        this.service = service;
    }

    @GetMapping("/definitions")
    @RequireRole({RoleEnum.CHILD, RoleEnum.PARENT})
    public Result<List<MedalDefinitionResp>> definitions() {
        return Result.ok(service.listDefinitions());
    }

    @GetMapping("/awards")
    @RequireRole({RoleEnum.CHILD, RoleEnum.PARENT})
    public Result<List<ChildMedalResp>> awards(@RequestParam @Positive Long childId) {
        return Result.ok(service.listAwards(childId));
    }
}
