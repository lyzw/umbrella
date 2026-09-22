package cn.studykid.growthplanet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

public record MenuUpsertResp(@JsonFormat(shape = JsonFormat.Shape.STRING) Long menuId) {
}
