package com.ecommerce.vo;

import lombok.Data;

@Data
public class ReportVO {

    private Long id;
    private Long reporterId;
    private String reporterName;

    /** PRODUCT / SHOP */
    private String targetType;
    private Long targetId;
    private String targetTitle;
    private String targetCover;

    private Long shopId;
    private String shopName;

    private String type;
    private String content;

    /** PENDING / ACCEPTED / REJECTED */
    private String status;
    private String handleNote;
    private String handlerName;
    private String handledAt;

    private String createdAt;
}
