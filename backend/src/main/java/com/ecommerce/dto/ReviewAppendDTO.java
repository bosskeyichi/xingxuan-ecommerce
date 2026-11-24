package com.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 追评入参 */
@Data
public class ReviewAppendDTO {

    @NotBlank(message = "追加内容不能为空")
    @Size(max = 500, message = "追加内容不能超过 500 个字符")
    private String content;
}
