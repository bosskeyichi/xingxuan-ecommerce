package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.common.Result;
import com.ecommerce.dto.MessageSendDTO;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.interceptor.LoginInterceptor;
import com.ecommerce.service.MessageService;
import com.ecommerce.vo.ConversationVO;
import com.ecommerce.vo.MessageVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 消息：好友私聊、店铺客服、系统通知。无 WebSocket，前端按需轮询。
 */
@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
@Validated
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/conversations")
    public Result<List<ConversationVO>> conversations(@RequestParam(required = false) String scene) {
        return Result.success(messageService.conversations(requireLogin(), scene));
    }

    @GetMapping("/history")
    public Result<IPage<MessageVO>> history(
            @RequestParam Long peerId,
            @RequestParam(required = false) String scene,
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size) {
        return Result.success(messageService.history(requireLogin(), peerId, scene, current, size));
    }

    @PostMapping
    public Result<MessageVO> send(@RequestBody @Valid MessageSendDTO dto) {
        return Result.success(messageService.send(requireLogin(), dto));
    }

    @PostMapping("/read")
    public Result<Integer> read(@RequestParam Long peerId, @RequestParam(required = false) String scene) {
        return Result.success(messageService.markRead(requireLogin(), peerId, scene));
    }

    /** 从会话列表删除会话（仅不再显示，消息不删除；对方再来新消息会自动恢复） */
    @PostMapping("/hide")
    public Result<Void> hide(@RequestParam Long peerId, @RequestParam(required = false) String scene) {
        messageService.hideConversation(requireLogin(), peerId, scene);
        return Result.success("会话已删除", null);
    }

    @GetMapping("/unread")
    public Result<Map<String, Object>> unread() {
        return Result.success(messageService.unreadSummary(requireLogin()));
    }

    @GetMapping("/system")
    public Result<IPage<MessageVO>> system(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size) {
        return Result.success(messageService.systemMessages(requireLogin(), current, size));
    }

    /** 商品页「联系客服」：按店铺拿客服账号 */
    @GetMapping("/service-target")
    public Result<Map<String, Object>> serviceTarget(@RequestParam Long shopId) {
        return Result.success(messageService.serviceTarget(shopId));
    }

    /** 会话对方资料：临时会话标题用，别让出现「用户 #5」这类占位名 */
    @GetMapping("/peer")
    public Result<Map<String, Object>> peer(
            @RequestParam Long peerId,
            @RequestParam(required = false) String scene) {
        return Result.success(messageService.peerCard(requireLogin(), peerId, scene));
    }

    private Long requireLogin() {
        Long uid = LoginInterceptor.USER_ID.get();
        if (uid == null) throw new BusinessException(401, "请先登录");
        return uid;
    }
}
