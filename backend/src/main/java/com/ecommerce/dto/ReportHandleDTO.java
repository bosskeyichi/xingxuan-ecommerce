package com.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReportHandleDTO {

    /** ACCEPTED 举报成立 / REJECTED 驳回 */
    @NotBlank(message = "请选择处理结果")
    @Pattern(regexp = "ACCEPTED|REJECTED", message = "处理结果取值不合法")
    private String status;

    @Size(max = 500, message = "处理说明不能超过 500 个字符")
    private String note;
}
