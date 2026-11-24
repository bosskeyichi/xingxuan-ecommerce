package com.ecommerce.service;

import com.ecommerce.dto.SettingDTO;
import com.ecommerce.entity.UserSetting;

/**
 * 个人设置：隐私与消息开关。
 * 读取时若账号还没有设置行，返回一份默认值（不落库），保存时才真正写入。
 */
public interface SettingService {

    UserSetting get(Long userId);

    void save(Long userId, SettingDTO dto);

    /** 是否允许被搜索到，默认允许 */
    boolean canSearch(Long userId);

    /** 是否允许被加好友，默认允许 */
    boolean canBeFriend(Long userId);

    /** 是否公开邮箱 / 手机号 / 评价 */
    boolean showEmail(Long userId);

    boolean showPhone(Long userId);

    boolean showReviews(Long userId);

    /**
     * 能否给该用户发对应场景的消息。
     *
     * @param scene FRIEND / SERVICE / SYSTEM
     */
    boolean canReceive(Long userId, String scene);
}
