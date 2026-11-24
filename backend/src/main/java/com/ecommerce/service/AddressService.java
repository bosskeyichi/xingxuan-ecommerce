package com.ecommerce.service;

import com.ecommerce.dto.AddressDTO;
import com.ecommerce.entity.Address;

import java.util.List;

public interface AddressService {

    /** 我的地址簿（默认地址排最前） */
    List<Address> list(Long userId);

    Address add(Long userId, AddressDTO dto);

    Address update(Long userId, Long id, AddressDTO dto);

    void remove(Long userId, Long id);

    /** 设为默认地址（原默认自动取消） */
    void setDefault(Long userId, Long id);
}
