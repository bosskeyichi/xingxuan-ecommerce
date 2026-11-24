package com.ecommerce.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.dto.OrderSubmitDTO;
import com.ecommerce.dto.ShipDTO;
import com.ecommerce.vo.OrderVO;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

public interface OrderService {

    /**
     * 提交订单：购物车中的商品按店铺分组，每个店铺生成一笔独立订单。
     * 返回按创建顺序排列的订单号（跨店时数量大于 1）。
     */
    List<String> submit(Long userId, OrderSubmitDTO dto);

    OrderVO detail(Long userId, String orderNo);

    IPage<OrderVO> page(Long userId, String status, long current, long size);

    /** 模拟支付 */
    void mockPay(Long userId, String orderNo);

    /** 合并支付：跨店结算会拆成多笔订单，支付可一次完成，返回实际支付笔数 */
    int mockPayBatch(Long userId, List<String> orderNos);

    /** 取消订单（仅待支付可取消，回退库存并还原购物车） */
    void cancel(Long userId, String orderNo);

    /** 用户确认收货：SHIPPED -> COMPLETED */
    void confirmReceipt(Long userId, String orderNo);

    /** 用户删除历史订单（仅已完成 / 已取消） */
    void delete(Long userId, String orderNo);

    /** 用户批量删除历史订单，返回实际删除条数 */
    int batchDelete(Long userId, List<String> orderNos);

    /** 后台：删除历史订单（shopId 非空时校验归属） */
    void adminDelete(String orderNo, Long shopId);

    /** 后台：批量删除历史订单，返回实际删除条数 */
    int adminBatchDelete(List<String> orderNos, Long shopId);

    /** 后台：发货（可填写快递公司与单号） */
    void ship(String orderNo, Long shopId, ShipDTO dto);

    /** 后台：标记完成 */
    void complete(String orderNo, Long shopId);

    /** 后台：分页查询订单（商家只看自己店铺，平台管理员传 null 看全部） */
    IPage<OrderVO> adminPage(String status, long current, long size, Long shopId);

    /** 定时任务：自动取消超时未支付订单（分钟），返回取消数量 */
    int autoCancelExpired(int minutes);

    /** 后台：导出订单 CSV（带 UTF-8 BOM，Excel 可直接打开） */
    void exportCsv(HttpServletResponse response, Long shopId) throws IOException;
}
