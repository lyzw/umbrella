package cn.studykid.growthplanet.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.studykid.growthplanet.common.context.LoginUser;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.entity.FamilyMember;
import cn.studykid.growthplanet.entity.User;
import cn.studykid.growthplanet.mapper.FamilyMemberMapper;
import cn.studykid.growthplanet.mapper.UserMapper;
import cn.studykid.growthplanet.util.JwtUtil;
import org.springframework.stereotype.Service;
import java.util.Objects;
import java.util.UUID;

@Service
public class SessionService {
    private final UserMapper users;
    private final FamilyMemberMapper members;
    private final JwtUtil jwt;

    public SessionService(UserMapper users, FamilyMemberMapper members, JwtUtil jwt) {
        this.users = users;
        this.members = members;
        this.jwt = jwt;
    }

    public LoginUser authenticate(String token) {
        var claims = jwt.parse(token);
        User user = users.selectById(jwt.getUserId(claims));
        Number version = claims.get("token_version", Number.class);
        if (user == null || !"NORMAL".equals(user.getStatus()) || version == null
                || !Objects.equals(user.getTokenVersion(), version.longValue())
                || !Objects.equals(user.getRole(), jwt.getRole(claims))) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH);
        }
        LoginUser current = current(user);
        current.setJti(jwt.getJti(claims));
        return current;
    }

    public LoginUser current(User user) {
        var familyIds = members.selectList(new QueryWrapper<FamilyMember>()
                        .eq("user_id", user.getId()).eq("bind_status", "BOUND").orderByAsc("id"))
                .stream().map(FamilyMember::getFamilyId).toList();
        return new LoginUser(user.getId(), user.getRole(), familyIds,
                UUID.randomUUID().toString(), user.getTokenVersion());
    }
}
