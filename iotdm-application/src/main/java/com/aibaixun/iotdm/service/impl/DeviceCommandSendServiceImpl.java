package com.aibaixun.iotdm.service.impl;

import com.aibaixun.iotdm.entity.DeviceCommandSendEntity;
import com.aibaixun.iotdm.enums.SendStatus;
import com.aibaixun.iotdm.mapper.DeviceCommandSendMapper;
import com.aibaixun.iotdm.service.IDeviceCommandSendService;
import com.aibaixun.iotdm.util.UserInfoUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

/**
 * <p>
 * 命令下发记录 服务实现类
 * </p>
 *
 * @author baixun
 * @since 2022-03-03
 */
@Service
public class DeviceCommandSendServiceImpl extends ServiceImpl<DeviceCommandSendMapper, DeviceCommandSendEntity> implements IDeviceCommandSendService {


    @Override
    public Page<DeviceCommandSendEntity> pageQueryDeviceCommandSend(String deviceId, String commandLabel, String commandId, Long startTs, Long endTs, Integer page,Integer pageSize) {


        String tenantId = UserInfoUtil.getTenantIdOfNull();
        LambdaQueryWrapper<DeviceCommandSendEntity> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(DeviceCommandSendEntity::getTenantId,tenantId);
        if (StringUtils.isNotBlank(deviceId)){
            queryWrapper.eq(DeviceCommandSendEntity::getDeviceId,deviceId);
        }
        if (StringUtils.isNotBlank(commandId)){
            queryWrapper.eq(DeviceCommandSendEntity::getCommandId,commandId);
        }
        if (StringUtils.isNotBlank(commandLabel)){
            queryWrapper.likeRight(DeviceCommandSendEntity::getCommandLabel,commandLabel);
        }
        if (Objects.nonNull(startTs) && Objects.nonNull(endTs)){
            queryWrapper.between(DeviceCommandSendEntity::getRespTs,startTs,endTs);
        }
        queryWrapper.orderByDesc(DeviceCommandSendEntity::getRespTs);
        return page(Page.of(page,pageSize),queryWrapper);
    }


    @Override
    public Boolean updateDeviceCommandStatus2Received(String deviceId, Integer msgId) {
        LambdaUpdateWrapper<DeviceCommandSendEntity> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(DeviceCommandSendEntity::getDeviceId,deviceId).eq(DeviceCommandSendEntity::getMsgId,msgId).eq(DeviceCommandSendEntity::getSendStatus, SendStatus.SEND);
        updateWrapper.set(DeviceCommandSendEntity::getSendStatus, SendStatus.SEND_ARRIVE).set(DeviceCommandSendEntity::getRespTs, Instant.now().toEpochMilli());
        return update(updateWrapper);
    }

    @Override
    public Boolean updateDeviceCommandToSetMsgId(Integer sendId, Integer msgId) {
        LambdaUpdateWrapper<DeviceCommandSendEntity> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(DeviceCommandSendEntity::getReqId,sendId)
                .eq(DeviceCommandSendEntity::getSendStatus, SendStatus.SEND)
                .set(DeviceCommandSendEntity::getMsgId,msgId)
                .set(DeviceCommandSendEntity::getRespTs, Instant.now().toEpochMilli());
        return update(updateWrapper);
    }


    @Override
    public Boolean updateDeviceCommand(String deviceId, Integer reqId, SendStatus targetStatus) {
        LambdaUpdateWrapper<DeviceCommandSendEntity> updateWrapper = Wrappers.<DeviceCommandSendEntity>lambdaUpdate()
                .eq(DeviceCommandSendEntity::getDeviceId,deviceId)
                .eq(DeviceCommandSendEntity::getReqId,reqId)
                .ne(DeviceCommandSendEntity::getSendStatus, SendStatus.SUCCESS);
        updateWrapper.set(DeviceCommandSendEntity::getSendStatus, targetStatus);
        return update(updateWrapper);
    }
}
