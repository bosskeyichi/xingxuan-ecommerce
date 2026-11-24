package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ecommerce.dto.AddressDTO;
import com.ecommerce.entity.Address;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.mapper.AddressMapper;
import com.ecommerce.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    /** 每个用户最多 20 条地址 */
    private static final long MAX_ADDRESS = 20;

    private final AddressMapper addressMapper;

    @Override
    public List<Address> list(Long userId) {
        return addressMapper.selectList(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, userId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getId));
    }

    @Override
    @Transactional
    public Address add(Long userId, AddressDTO dto) {
        Long count = addressMapper.selectCount(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, userId));
        if (count != null && count >= MAX_ADDRESS) {
            throw new BusinessException("最多可保存 " + MAX_ADDRESS + " 条收货地址");
        }
        Address a = new Address();
        apply(a, dto);
        a.setUserId(userId);
        boolean makeDefault = isDefault(dto);
        if (makeDefault || count == null || count == 0) {
            a.setIsDefault(1);
            clearDefault(userId, null);
        } else {
            a.setIsDefault(0);
        }
        addressMapper.insert(a);
        return a;
    }

    @Override
    @Transactional
    public Address update(Long userId, Long id, AddressDTO dto) {
        Address old = own(userId, id);
        apply(old, dto);
        if (isDefault(dto) && (old.getIsDefault() == null || old.getIsDefault() != 1)) {
            clearDefault(userId, id);
            old.setIsDefault(1);
        }
        addressMapper.updateById(old);
        return old;
    }

    @Override
    @Transactional
    public void remove(Long userId, Long id) {
        Address a = own(userId, id);
        addressMapper.deleteById(a.getId());
        // 若删除的是默认地址，把最新一条提升为默认
        if (a.getIsDefault() != null && a.getIsDefault() == 1) {
            List<Address> rest = list(userId);
            if (!rest.isEmpty()) {
                Address first = rest.get(0);
                Address up = new Address();
                up.setId(first.getId());
                up.setIsDefault(1);
                addressMapper.updateById(up);
            }
        }
    }

    @Override
    @Transactional
    public void setDefault(Long userId, Long id) {
        own(userId, id);
        clearDefault(userId, id);
        Address up = new Address();
        up.setId(id);
        up.setIsDefault(1);
        addressMapper.updateById(up);
    }

    // ---------- 内部工具 ----------

    private Address own(Long userId, Long id) {
        Address a = addressMapper.selectById(id);
        if (a == null || !a.getUserId().equals(userId)) {
            throw new BusinessException("地址不存在");
        }
        return a;
    }

    private void apply(Address a, AddressDTO dto) {
        a.setReceiverName(dto.getReceiverName().trim());
        a.setReceiverPhone(dto.getReceiverPhone().trim());
        a.setRegion(dto.getRegion().trim());
        a.setDetail(dto.getDetail().trim());
    }

    private boolean isDefault(AddressDTO dto) {
        return dto.getIsDefault() != null && dto.getIsDefault() == 1;
    }

    /** 取消该用户其它地址的默认标记（keepId 不清） */
    private void clearDefault(Long userId, Long keepId) {
        LambdaUpdateWrapper<Address> u = new LambdaUpdateWrapper<Address>()
                .eq(Address::getUserId, userId)
                .eq(Address::getIsDefault, 1)
                .set(Address::getIsDefault, 0);
        if (keepId != null) u.ne(Address::getId, keepId);
        addressMapper.update(null, u);
    }
}
