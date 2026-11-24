package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.dto.ChangePasswordDTO;
import com.ecommerce.dto.LoginDTO;
import com.ecommerce.dto.ProfileDTO;
import com.ecommerce.dto.RegisterDTO;
import com.ecommerce.entity.User;
import com.ecommerce.vo.LoginVO;

import java.util.List;

public interface UserService {

    LoginVO login(LoginDTO dto);

    User register(RegisterDTO dto);

    User getById(Long id);

    User getCurrent();

    List<User> listAll();

    void updateStatus(Long id, Integer status);

    void delete(Long id);

    /** 重置用户数据：清空业务/店铺内容，账号资料与密码恢复默认 */
    void resetData(Long id);

    /** 角色调整：普通用户 ↔ 普通管理员（商家） */
    void changeRole(Long id, String role);

    /** 更新当前登录用户资料 */
    User updateProfile(Long userId, ProfileDTO dto);

    /** 修改密码 */
    void changePassword(Long userId, ChangePasswordDTO dto);

    /** 后台用户分页 */
    Page<User> page(long current, long size, String keyword);

    /** 超级管理员主动给指定用户发送系统通知（强制送达，不受接收方开关限制） */
    void notifyUser(Long toId, String content);
}
