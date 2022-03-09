package com.aibaixun.iotdm.server;

import com.aibaixun.iotdm.entity.DeviceEntity;
import com.aibaixun.iotdm.entity.ProductEntity;
import com.aibaixun.iotdm.enums.DeviceStatus;
import com.aibaixun.iotdm.msg.DeviceAuthSecretReqMsg;
import com.aibaixun.iotdm.msg.DeviceInfo;
import com.aibaixun.iotdm.service.BaseSqlInfoService;
import com.aibaixun.iotdm.service.DeviceInfoService;
import com.aibaixun.iotdm.service.IDeviceService;
import com.aibaixun.iotdm.service.IProductService;
import com.aibaixun.iotdm.transport.MqttTransportException;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 设备信息
 * @author wangxiao@aibaixun.com
 * @date 2022/3/8
 */
@Service
public class DeviceInfoServiceImpl extends BaseSqlInfoService implements DeviceInfoService {

    private IDeviceService deviceService;

    private IProductService productService;

    @Override
    public ListenableFuture<DeviceInfo> mqttDeviceAuthBySecret(DeviceAuthSecretReqMsg deviceAuthSecretReqMsg) {
        return sqlExecutorService.submit(()->{
            DeviceEntity deviceEntity = deviceService.queryBy3Param(deviceAuthSecretReqMsg.getClientId(), deviceAuthSecretReqMsg.getUsername(), deviceAuthSecretReqMsg.getPassword());
            if (Objects.isNull(deviceEntity)){
                throw new MqttTransportException((byte)4);
            }
            return toData(deviceEntity);
        });
    }



    private DeviceInfo toData (DeviceEntity deviceEntity){
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.setDeviceId(deviceEntity.getId());
        deviceInfo.setDeviceCode(deviceEntity.getDeviceCode());
        deviceInfo.setProductId(deviceEntity.getProductId());
        if (StringUtils.isNotBlank(deviceEntity.getProductId())){
            ProductEntity product = productService.getById(deviceEntity.getProductId());
            if (Objects.nonNull(product)){
                deviceInfo.setProtocolType(product.getProtocolType());
                deviceInfo.setDataFormat(product.getDataFormat());
            }
        }
        return deviceInfo;
    }


    @Override
    public ListenableFuture<Boolean> setDeviceStatus2OnLine(String deviceId) {
        return sqlExecutorService.submit(()->deviceService.updateDeviceStatus(deviceId, DeviceStatus.ONLINE));
    }

    @Override
    public ListenableFuture<Boolean> setDeviceStatus2OffOnLine(String deviceId) {
        return sqlExecutorService.submit(()->deviceService.updateDeviceStatus(deviceId, DeviceStatus.OFFLINE));
    }


    @Override
    public ListenableFuture<Boolean> setDeviceStatus2Warn(String deviceId) {
        return sqlExecutorService.submit(()->deviceService.updateDeviceStatus(deviceId, DeviceStatus.WARN));
    }

    @Autowired
    public void setDeviceService(IDeviceService deviceService) {
        this.deviceService = deviceService;
    }


    @Autowired
    public void setProductService(IProductService productService) {
        this.productService = productService;
    }
}
