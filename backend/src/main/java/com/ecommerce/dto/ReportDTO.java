package com.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReportDTO {

    /** PRODUCT / SHOP */
    @NotBlank(message = "举报对象类型不能为空")
    private String targetType;

    @NotNull(message = "举报对象不能为空")
    private Long targetId;

    /** 假冒伪劣 / 虚假宣传 / 违规内容 / 侵权 / 其他 */
    @NotBlank(message = "请选择举报类型")
    @Size(max = 30, message = "举报类型过长")
    private String type;

    @NotBlank(message = "请填写举报说明")
    @Size(max = 500, message = "举报说明不能超过 500 个字符")
    private String content;
}
