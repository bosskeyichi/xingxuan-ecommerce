package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.dto.ReportDTO;
import com.ecommerce.dto.ReportHandleDTO;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.Report;
import com.ecommerce.entity.Shop;
import com.ecommerce.entity.User;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.ReportMapper;
import com.ecommerce.mapper.ShopMapper;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.service.MessageService;
import com.ecommerce.service.ReportService;
import com.ecommerce.vo.ReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final String PENDING = "PENDING";
    private static final String ROLE_ADMIN = "ADMIN";
    /** 与前端举报弹窗的选项保持一致，防止脏数据进后台筛选 */
    private static final Set<String> REPORT_TYPES = Set.of("假冒伪劣", "虚假宣传", "违规内容", "侵权", "其他");

    private final ReportMapper reportMapper;
    private final ProductMapper productMapper;
    private final ShopMapper shopMapper;
    private final UserMapper userMapper;
    private final MessageService messageService;

    @Override
    public void submit(Long uid, ReportDTO dto) {
        String targetType = dto.getTargetType() == null ? "" : dto.getTargetType().trim().toUpperCase();
        if (!"PRODUCT".equals(targetType) && !"SHOP".equals(targetType)) {
            throw new BusinessException("举报对象类型不合法");
        }
        String reportType = dto.getType() == null ? "" : dto.getType().trim();
        if (!REPORT_TYPES.contains(reportType)) {
            throw new BusinessException("举报类型不合法");
        }

        Long shopId;
        String title;
        Long ownerId;
        if ("PRODUCT".equals(targetType)) {
            Product p = productMapper.selectById(dto.getTargetId());
            if (p == null) throw new BusinessException("商品不存在或已下架");
            shopId = p.getShopId();
            title = p.getName();
            Shop shop = shopId == null ? null : shopMapper.selectById(shopId);
            ownerId = shop == null ? null : shop.getOwnerId();
        } else {
            Shop shop = shopMapper.selectById(dto.getTargetId());
            if (shop == null) throw new BusinessException("店铺不存在");
            shopId = shop.getId();
            title = shop.getName();
            ownerId = shop.getOwnerId();
        }
        if (uid.equals(ownerId)) {
            throw new BusinessException("不能举报自己的商品或店铺");
        }

        Long exists = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, uid)
                .eq(Report::getTargetType, targetType)
                .eq(Report::getTargetId, dto.getTargetId())
                .eq(Report::getStatus, PENDING));
        if (exists != null && exists > 0) {
            throw new BusinessException("你已举报过该" + ("PRODUCT".equals(targetType) ? "商品" : "店铺") + "，请等待处理");
        }

        Report r = new Report();
        r.setReporterId(uid);
        r.setTargetType(targetType);
        r.setTargetId(dto.getTargetId());
        r.setShopId(shopId);
        r.setType(reportType);
        r.setContent(dto.getContent().trim());
        r.setStatus(PENDING);
        reportMapper.insert(r);

        String kind = "PRODUCT".equals(targetType) ? "商品" : "店铺";
        String shopName = "";
        if (shopId != null) {
            Shop s = shopMapper.selectById(shopId);
            shopName = s == null ? "" : s.getName();
        }

        // 1) 通知被举报对象所属店铺的管理员（店主）
        if (ownerId != null && !ownerId.equals(uid)) {
            messageService.sendSystem(ownerId, "收到一条新的" + kind + "举报：" + title
                    + (shopName.isEmpty() ? "" : "（店铺：" + shopName + "）")
                    + "，请到「举报管理」查看处理。");
        }

        // 2) 同步通知平台（超级管理员），形成店铺 + 平台双线可查
        List<User> admins = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, ROLE_ADMIN)
                .eq(User::getStatus, 1));
        for (User admin : admins) {
            if (admin.getId().equals(uid) || admin.getId().equals(ownerId)) continue;
            messageService.sendSystem(admin.getId(), "平台监管提醒：有用户举报" + kind + "「" + title + "」"
                    + (shopName.isEmpty() ? "" : "（店铺：" + shopName + "）")
                    + "，类型：" + reportType + "，请到「举报管理」跟进。");
        }
    }

    @Override
    public IPage<ReportVO> mine(Long uid, long current, long size) {
        Page<Report> page = reportMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getReporterId, uid)
                        .orderByDesc(Report::getId));
        return toVOs(page);
    }

    @Override
    public IPage<ReportVO> adminPage(Long shopId, String status, String keyword, long current, long size) {
        String kw = keyword == null ? "" : keyword.trim();
        LambdaQueryWrapper<Report> w = new LambdaQueryWrapper<Report>()
                .eq(shopId != null, Report::getShopId, shopId)
                .eq(status != null && !status.trim().isEmpty(), Report::getStatus,
                        status == null ? null : status.trim().toUpperCase())
                .orderByAsc(Report::getStatus)
                .orderByDesc(Report::getId);
        if (!kw.isEmpty()) {
            w.and(x -> x.like(Report::getContent, kw).or().like(Report::getType, kw));
        }
        Page<Report> page = reportMapper.selectPage(new Page<>(current, size), w);
        return toVOs(page);
    }

    @Override
    @Transactional
    public void handle(Long uid, Long id, ReportHandleDTO dto, Long shopId) {
        Report r = reportMapper.selectById(id);
        if (r == null) throw new BusinessException("举报不存在");
        if (shopId != null && !shopId.equals(r.getShopId())) {
            throw new BusinessException(403, "无权处理其他店铺的举报");
        }
        if (!PENDING.equals(r.getStatus())) {
            throw new BusinessException("该举报已处理，请勿重复操作");
        }

        r.setStatus(dto.getStatus());
        r.setHandleNote(dto.getNote() == null ? null : dto.getNote().trim());
        r.setHandlerId(uid);
        r.setHandledAt(LocalDateTime.now());
        reportMapper.updateById(r);

        String result = "ACCEPTED".equals(dto.getStatus()) ? "举报成立，已处理" : "经核实暂不成立";
        String note = (dto.getNote() == null || dto.getNote().trim().isEmpty())
                ? "" : "。处理说明：" + dto.getNote().trim();
        messageService.sendSystem(r.getReporterId(),
                "你对" + ("PRODUCT".equals(r.getTargetType()) ? "商品" : "店铺")
                        + "的举报已处理：" + result + note);
    }

    @Override
    public long pendingCount(Long shopId) {
        Long c = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(shopId != null, Report::getShopId, shopId)
                .eq(Report::getStatus, PENDING));
        return c == null ? 0 : c;
    }

    private IPage<ReportVO> toVOs(Page<Report> page) {
        List<Report> list = page.getRecords();
        if (list == null || list.isEmpty()) {
            return new Page<ReportVO>(page.getCurrent(), page.getSize(), page.getTotal())
                    .setRecords(Collections.emptyList());
        }

        Set<Long> userIds = new HashSet<>();
        Set<Long> productIds = new HashSet<>();
        Set<Long> shopIds = new HashSet<>();
        for (Report r : list) {
            if (r.getReporterId() != null) userIds.add(r.getReporterId());
            if (r.getHandlerId() != null) userIds.add(r.getHandlerId());
            if (r.getShopId() != null) shopIds.add(r.getShopId());
            if ("PRODUCT".equals(r.getTargetType()) && r.getTargetId() != null) productIds.add(r.getTargetId());
            if ("SHOP".equals(r.getTargetType()) && r.getTargetId() != null) shopIds.add(r.getTargetId());
        }

        Map<Long, User> users = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, Product> products = productIds.isEmpty() ? Collections.emptyMap()
                : productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        Map<Long, Shop> shops = shopIds.isEmpty() ? Collections.emptyMap()
                : shopMapper.selectBatchIds(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, s -> s, (a, b) -> a));

        List<ReportVO> vos = new ArrayList<>(list.size());
        for (Report r : list) {
            ReportVO vo = new ReportVO();
            vo.setId(r.getId());
            vo.setReporterId(r.getReporterId());
            vo.setReporterName(name(users.get(r.getReporterId()), r.getReporterId()));
            vo.setTargetType(r.getTargetType());
            vo.setTargetId(r.getTargetId());
            vo.setShopId(r.getShopId());
            Shop shop = r.getShopId() == null ? null : shops.get(r.getShopId());
            vo.setShopName(shop == null ? null : shop.getName());
            vo.setType(r.getType());
            vo.setContent(r.getContent());
            vo.setStatus(r.getStatus());
            vo.setHandleNote(r.getHandleNote());
            vo.setHandlerName(r.getHandlerId() == null ? null : name(users.get(r.getHandlerId()), r.getHandlerId()));
            vo.setHandledAt(r.getHandledAt() == null ? null : r.getHandledAt().format(FMT));
            vo.setCreatedAt(r.getCreatedAt() == null ? null : r.getCreatedAt().format(FMT));

            if ("PRODUCT".equals(r.getTargetType())) {
                Product p = products.get(r.getTargetId());
                if (p != null) {
                    vo.setTargetTitle(p.getName());
                    vo.setTargetCover(p.getCover());
                }
            } else {
                Shop s = shops.get(r.getTargetId());
                if (s != null) {
                    vo.setTargetTitle(s.getName());
                    vo.setTargetCover(s.getCover());
                }
            }
            vos.add(vo);
        }
        return new Page<ReportVO>(page.getCurrent(), page.getSize(), page.getTotal()).setRecords(vos);
    }

    private String name(User u, Long fallbackId) {
        if (u == null) return "用户 #" + fallbackId;
        return u.getNickname() == null || u.getNickname().isEmpty() ? u.getUsername() : u.getNickname();
    }
}
