package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.dto.SettingDTO;
import com.ecommerce.entity.UserSetting;
import com.ecommerce.mapper.UserSettingMapper;
import com.ecommerce.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingServiceImpl implements SettingService {

    private final UserSettingMapper settingMapper;

    @Override
    public UserSetting get(Long userId) {
        UserSetting s = find(userId);
        return s == null ? defaults(userId) : s;
    }

    @Override
    @Transactional
    public void save(Long userId, SettingDTO dto) {
        UserSetting s = find(userId);
        boolean create = s == null;
        if (create) {
            s = defaults(userId);
        }
        if (dto.getSearchable() != null) s.setSearchable(flag(dto.getSearchable()));
        if (dto.getFriendPolicy() != null) s.setFriendPolicy(dto.getFriendPolicy());
        if (dto.getShowEmail() != null) s.setShowEmail(flag(dto.getShowEmail()));
        if (dto.getShowPhone() != null) s.setShowPhone(flag(dto.getShowPhone()));
        if (dto.getShowReviews() != null) s.setShowReviews(flag(dto.getShowReviews()));
        if (dto.getReceiveFriendMsg() != null) s.setReceiveFriendMsg(flag(dto.getReceiveFriendMsg()));
        if (dto.getReceiveServiceMsg() != null) s.setReceiveServiceMsg(flag(dto.getReceiveServiceMsg()));
        if (dto.getReceiveSystemMsg() != null) s.setReceiveSystemMsg(flag(dto.getReceiveSystemMsg()));
        if (dto.getNotifySound() != null) s.setNotifySound(flag(dto.getNotifySound()));
        if (create) {
            settingMapper.insert(s);
        } else {
            settingMapper.updateById(s);
        }
    }

    @Override
    public boolean canSearch(Long userId) {
        return enabled(get(userId).getSearchable());
    }

    @Override
    public boolean canBeFriend(Long userId) {
        return !"NOBODY".equals(get(userId).getFriendPolicy());
    }

    @Override
    public boolean showEmail(Long userId) {
        return enabled(get(userId).getShowEmail());
    }

    @Override
    public boolean showPhone(Long userId) {
        return enabled(get(userId).getShowPhone());
    }

    @Override
    public boolean showReviews(Long userId) {
        return enabled(get(userId).getShowReviews());
    }

    @Override
    public boolean canReceive(Long userId, String scene) {
        UserSetting s = get(userId);
        if ("SERVICE".equalsIgnoreCase(scene)) return enabled(s.getReceiveServiceMsg());
        if ("SYSTEM".equalsIgnoreCase(scene)) return enabled(s.getReceiveSystemMsg());
        return enabled(s.getReceiveFriendMsg());
    }

    private UserSetting find(Long userId) {
        if (userId == null) return null;
        return settingMapper.selectOne(new LambdaQueryWrapper<UserSetting>()
                .eq(UserSetting::getUserId, userId)
                .last("LIMIT 1"));
    }

    private UserSetting defaults(Long userId) {
        UserSetting s = new UserSetting();
        s.setUserId(userId);
        s.setSearchable(1);
        s.setFriendPolicy("ALL");
        s.setShowEmail(0);
        s.setShowPhone(0);
        s.setShowReviews(1);
        s.setReceiveFriendMsg(1);
        s.setReceiveServiceMsg(1);
        s.setReceiveSystemMsg(1);
        s.setNotifySound(1);
        return s;
    }

    private boolean enabled(Integer v) {
        return v == null || v == 1;
    }

    private int flag(Integer v) {
        return v != null && v == 1 ? 1 : 0;
    }
}
