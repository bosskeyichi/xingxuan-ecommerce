package com.ecommerce.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.dto.FriendAddDTO;
import com.ecommerce.vo.UserCardVO;

import java.util.List;
import java.util.Set;

/**
 * 好友：申请-同意制。用户端只在普通用户之间、管理端只在商家之间互加；
 * 超级管理员默认拥有全部商家好友（无需申请）。
 */
public interface FriendService {

    /**
     * 搜索用户（按昵称 / 用户名 / ID），自动按当前身份限定搜索范围。
     * 关键词为空时返回空结果：不允许不加好友就浏览全部用户（隐私）。
     *
     * @param filter 分类筛选：ALL 全部结果 / ADDABLE 可申请 / FRIEND 已是好友
     */
    IPage<UserCardVO> search(Long currentUserId, String keyword, String filter, long current, long size);

    /** 好友列表（超级管理员返回全部商家） */
    List<UserCardVO> listFriends(Long currentUserId);

    /**
     * 单个用户卡片（用户主页用）。非好友且对方关闭「允许被搜索」时不公开资料。
     */
    UserCardVO card(Long currentUserId, Long targetId);

    /** 发送好友申请（对方同意后才能聊天） */
    void add(Long currentUserId, FriendAddDTO dto);

    /** 收到的好友申请列表 */
    List<UserCardVO> requests(Long currentUserId);

    /** 同意好友申请 */
    void accept(Long currentUserId, Long requesterId);

    /** 拒绝好友申请 */
    void reject(Long currentUserId, Long requesterId);

    void remove(Long currentUserId, Long friendId);

    /** 好友 id 集合；超级管理员与商家的关系视为默认好友 */
    Set<Long> friendIds(Long currentUserId);

    boolean isFriend(Long currentUserId, Long targetId);
}
