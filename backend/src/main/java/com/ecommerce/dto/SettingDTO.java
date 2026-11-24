package com.ecommerce.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 个人设置：所有字段可空，为空表示不修改；前端整表单提交。
 */
@Data
public class SettingDTO {

    /** 1 允许被搜索 / 0 不允许 */
    private Integer searchable;

    /** ALL / NOBODY */
    @Pattern(regexp = "ALL|NOBODY", message = "好友权限取值不合法")
    private String friendPolicy;

    private Integer showEmail;
    private Integer showPhone;
    private Integer showReviews;

    private Integer receiveFriendMsg;
    private Integer receiveServiceMsg;
    private Integer receiveSystemMsg;
    private Integer notifySound;
}
