package com.ecommerce.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MessageSendDTO {

    /** 接收人；客服会话时由后端按店铺归属校正 */
    private Long toId;

    /** FRIEND / SERVICE */
    private String scene;

    /** TEXT / PRODUCT / SHOP / REVIEW */
    private String type;

    @Size(max = 2000, message = "消息内容不能超过 2000 个字符")
    private String content;

    /** 分享的商品 / 店铺 / 评价 id */
    private Long targetId;

    /** 客服会话的店铺 id */
    private Long shopId;

    /**
     * 校验用：TEXT 必须有内容；分享卡片必须有 targetId。
     * 在 Service 中调用，别让 TEXT 与卡片走两套 DTO。
     */
    public void validate() {
        String t = type == null ? "TEXT" : type.toUpperCase();
        if ("TEXT".equals(t)) {
            if (content == null || content.trim().isEmpty()) {
                throw new com.ecommerce.exception.BusinessException("消息内容不能为空");
            }
        } else if (targetId == null) {
            throw new com.ecommerce.exception.BusinessException("分享内容不存在");
        }
    }

    public String sceneOrDefault() {
        return scene == null || scene.trim().isEmpty() ? "FRIEND" : scene.trim().toUpperCase();
    }
}
