package com.ecommerce.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ReviewBatchDeleteDTO {

    @NotEmpty(message = "请选择要删除的评价")
    private List<Long> ids;
}
