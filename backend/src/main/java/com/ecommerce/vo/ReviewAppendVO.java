package com.ecommerce.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReviewAppendVO {
    private Long id;
    private String content;
    private LocalDateTime createdAt;
}
