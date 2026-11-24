package com.ecommerce.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.dto.ReportDTO;
import com.ecommerce.dto.ReportHandleDTO;
import com.ecommerce.vo.ReportVO;

/**
 * 举报：用户举报商品 / 店铺，商家处理本店举报，平台管理员处理全部。
 */
public interface ReportService {

    void submit(Long userId, ReportDTO dto);

    /** 我提交的举报（含处理结果） */
    IPage<ReportVO> mine(Long userId, long current, long size);

    /**
     * 后台举报列表。
     *
     * @param shopId 商家传本店 id；平台管理员传 null 表示看全部
     */
    IPage<ReportVO> adminPage(Long shopId, String status, String keyword, long current, long size);

    void handle(Long userId, Long id, ReportHandleDTO dto, Long shopId);

    long pendingCount(Long shopId);
}
