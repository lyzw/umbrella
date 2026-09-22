package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 心愿菜单提交单：孩子每天最多一份（(child_id, menu_date) 唯一）。
 * 明细复用 {@link ChildWantEat}（其 wish_id 指向本表 id），因此本表只承载"提交"语义：
 * status = SUBMITTED（已提交，当日锁定）/ WITHDRAWN（已撤回，可改选后重新提交）。
 * 无本表行 = 孩子当天未创建心愿菜单（功能可选，不产生占位记录）。
 * version 为手写乐观锁（项目未装配 OptimisticLockerInnerInterceptor，@Version 仅标注）。
 */
@Data
@TableName("life_wish_menu")
public class WishMenu {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long childId;
    private Long familyId;
    private LocalDate menuDate;
    private String status;
    /** 提交时快照的家长上限，用于回看当时口径。 */
    private Integer maxDishes;
    private Integer dishCount;
    private Integer submitVersion;
    private LocalDateTime submitTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long deleteAt = 0L;
    private Integer version;
}
