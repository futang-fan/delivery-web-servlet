package com.deliveryweb.service;

import com.deliveryweb.dao.AddressDao;
import com.deliveryweb.pojo.Address;
import com.deliveryweb.util.BizException;
import com.deliveryweb.util.Constants;
import com.deliveryweb.util.MyBatisUtil;

import java.time.LocalDateTime;
import java.util.List;


/**
 * 收货地址业务逻辑：增删改查、默认地址唯一性（先清后设，应用层保证）、越权防护。
 */
public final class AddressService {

    /** 联系方式：11 位数字（设计书 表2.8 via 表2.2 口径） */

    private AddressService() {
    }

    public static List<Address> list(Long userId) {
        return MyBatisUtil.withMapper(AddressDao.class, dao -> dao.listByUser(userId));
    }

    /**
     * 新增或修改地址；isDefault 为 true 时先清空该用户全部默认（先清后设），
     * 保证一个用户最多一个默认地址。修改操作带归属条件，越权时更新影响行数为 0。
     */
    public static void save(Long userId, Long id, String receiver, String phone,
                            String detail, boolean isDefault) {
        if (receiver == null || receiver.isEmpty()) {
            throw new BizException("请填写收货人姓名");
        }
        if (receiver.trim().length() > 50) {
            throw new BizException("收货人姓名不能超过 50 个字符");
        }
        if (phone == null || !Constants.PHONE_PATTERN.matcher(phone).matches()) {
            throw new BizException("收货人电话须为 11 位数字");
        }
        if (detail == null || detail.isEmpty()) {
            throw new BizException("请填写详细地址");
        }
        if (detail.trim().length() > 200) {
            throw new BizException("详细地址不能超过 200 个字符");
        }

        Address address = new Address();
        address.setUserId(userId);
        address.setReceiver(receiver.trim());
        address.setPhone(phone);
        address.setDetail(detail.trim());
        address.setIsDefault(isDefault);
        address.setId(id);

        MyBatisUtil.withTx(session -> {
            AddressDao dao = session.getMapper(AddressDao.class);
            if (isDefault) {
                dao.clearDefault(userId);
            }
            if (id == null) {
                address.setCreatedAt(LocalDateTime.now());
                dao.insert(address);
            } else if (dao.update(address) == 0) {
                throw new BizException("地址不存在或无权限");
            }
            return null;
        });
    }

    public static void delete(Long userId, Long id) {
        int rows = MyBatisUtil.withTx(session ->
                session.getMapper(AddressDao.class).delete(id, userId));
        if (rows == 0) {
            throw new BizException("地址不存在或无权限");
        }
    }

    /** 设为默认：先校验归属，再清空该用户全部默认并设本条为默认 */
    public static void setDefault(Long userId, Long id) {
        MyBatisUtil.withTx(session -> {
            AddressDao dao = session.getMapper(AddressDao.class);
            if (dao.findOwned(id, userId) == null) {
                throw new BizException("地址不存在或无权限");
            }
            dao.clearDefault(userId);
            if (dao.setDefault(id, userId) == 0) {
                throw new BizException("地址不存在或无权限");
            }
            return null;
        });
    }
}
