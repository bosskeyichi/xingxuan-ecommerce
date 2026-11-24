package com.ecommerce.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.dto.MessageSendDTO;
import com.ecommerce.vo.ConversationVO;
import com.ecommerce.vo.MessageVO;

import java.util.List;
import java.util.Map;

/**
 * 消息：好友私聊（FRIEND）、店铺客服（SERVICE）、系统通知（SYSTEM）。
 */
public interface MessageService {

    /** 会话列表，scene 为空返回全部；没有消息的好友也会返回空会话，方便主动发起 */
    List<ConversationVO> conversations(Long userId, String scene);

    IPage<MessageVO> history(Long userId, Long peerId, String scene, long current, long size);

    MessageVO send(Long userId, MessageSendDTO dto);

    int markRead(Long userId, Long peerId, String scene);

    /**
     * 从会话列表隐藏会话（仅不再显示，不删除消息；对方再来新消息会自动恢复）
     *
     * @return 是否隐藏成功
     */
    boolean hideConversation(Long userId, Long peerId, String scene);

    /** 系统通知全部标记已读（进入「系统通知」即视为看过） */
    void markSystemRead(Long userId);

    /**
     * 会话对方资料：用来从商品页 / 好友页跳转过来时补一个还没落库的临时会话，
     * 保证标题显示真实昵称而不是「用户 #5」这类占位名。
     */
    Map<String, Object> peerCard(Long userId, Long peerId, String scene);

    Map<String, Object> unreadSummary(Long userId);

    /** 系统通知（举报处理结果等） */
    IPage<MessageVO> systemMessages(Long userId, long current, long size);

    /** 店铺客服信息：店主账号 + 店铺名 */
    Map<String, Object> serviceTarget(Long shopId);

    /** 发送系统通知（from_id = 0） */
    void sendSystem(Long toId, String content);

    /** 官方（超级管理员）主动发送的系统通知：强制送达，不受接收方「拒绝系统消息」开关限制 */
    void sendOfficial(Long toId, String content);
}
