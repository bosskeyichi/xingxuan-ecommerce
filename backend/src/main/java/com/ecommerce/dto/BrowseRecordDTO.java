package com.ecommerce.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BrowseRecordDTO {

    @NotNull(message = "商品 ID 不能为空")
    private Long productId;
}
