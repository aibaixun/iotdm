package com.aibaixun.iotdm.server;


import com.aibaixun.basic.exception.BaseException;
import com.aibaixun.basic.result.BaseResultCode;
import com.aibaixun.basic.util.HexTool;
import com.aibaixun.common.util.JsonUtil;
import com.aibaixun.iotdm.data.BaseParam;
import com.aibaixun.iotdm.data.ToDeviceConfigParam;
import com.aibaixun.iotdm.entity.*;
import com.aibaixun.iotdm.enums.BusinessStep;
import com.aibaixun.iotdm.enums.SendStatus;
import com.aibaixun.iotdm.enums.DataFormat;
import com.aibaixun.iotdm.event.ToDeviceDisConnectEvent;
import com.aibaixun.iotdm.event.ToDeviceConfigEvent;
import com.aibaixun.iotdm.event.ToDeviceControlEvent;
import com.aibaixun.iotdm.event.ToDeviceOtaEvent;
import com.aibaixun.iotdm.script.JsInvokeService;
import com.aibaixun.iotdm.service.*;
import com.aibaixun.iotdm.support.ToDeviceCommandTransportData;
import com.aibaixun.iotdm.support.ToDeviceConfigTransportData;
import com.aibaixun.iotdm.support.ToDeviceType;
import com.aibaixun.iotdm.transport.SessionId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;


/**
 * 发送到设备 的消息处理类
 * @author wangxiao@aibaixun.com
 * @date 2022/3/16
 */
@Service
public class Default2DeviceProcessor implements ToDeviceProcessor{


    private IDeviceCommandSendService deviceCommandSendService;

    private IModelCommandService modelCommandService;

    private IProductService productService;

    private JsInvokeService jsInvokeService;

    private DeviceLogProcessor deviceLogProcessor;

    private IotDmEventPublisher eventPublisher;

    private IDeviceConfigSendService deviceConfigSendService;

    @Override
    public void processConfig(ToDeviceConfigParam toDeviceConfigParam,String productId) throws BaseException {
        String deviceId = toDeviceConfigParam.getDeviceId();
        ProductEntity product = productService.getById(productId);
        if (Objects.isNull(product)){
            throw new BaseException("产品不存在", BaseResultCode.GENERAL_ERROR);
        }

        String host = toDeviceConfigParam.getHost();
        Integer port = toDeviceConfigParam.getPort();
        String username = toDeviceConfigParam.getUsername();
        String clientId = toDeviceConfigParam.getClientId();
        String password = toDeviceConfigParam.getPassword();
        ToDeviceConfigTransportData deviceConfigTransportData = new ToDeviceConfigTransportData(ToDeviceType.CONFIG,host,port,clientId,username,password);

        String payload = JsonUtil.toJSONString(deviceConfigTransportData);
        if (DataFormat.BINARY.equals(product.getDataFormat())){
            try {
                Object o = jsInvokeService.invokeEncodeFunction(productId, deviceConfigTransportData);
                payload = HexTool.encodeHexStr((byte[]) o);
            }catch (Exception ignored){
            }
        }
        ToDeviceConfigEvent toDeviceConfigEvent = new ToDeviceConfigEvent(new SessionId(deviceId, productId), payload);
        DeviceConfigSendEntity deviceConfigSendEntity = new DeviceConfigSendEntity();
        deviceConfigSendEntity.setDeviceId(deviceId);
        deviceConfigSendEntity.setPayload(payload);
        deviceConfigSendEntity.setSendStatus(SendStatus.SEND);
        deviceConfigSendService.saveOrUpdateConfigSend(deviceConfigSendEntity);
        eventPublisher.publish2DeviceConfigReqEvent(toDeviceConfigEvent);
    }

    @Override
    public void processOta(String deviceId, String productId, String otaId) {
        eventPublisher.publish2DeviceOtaReqEvent(new ToDeviceOtaEvent(new SessionId(deviceId,productId),""));
    }

    @Override
    public void processControl(String deviceId, String productId, String commandId, Map<String, Object> params) throws BaseException {
        int reqId = ThreadLocalRandom.current().nextInt(2<<12);
        ProductEntity product = productService.getById(productId);
        if (Objects.isNull(product)){
            throw new BaseException("产品不存在", BaseResultCode.GENERAL_ERROR);
        }
        ModelCommandEntity modelCommand = modelCommandService.getById(commandId);
        if (Objects.isNull(modelCommand)){
            throw new BaseException("发送命令不存在", BaseResultCode.GENERAL_ERROR);
        }
        List<BaseParam> modelCommandParams = modelCommand.getParams();
        Map<String,Object> toDeviceParam = new HashMap<>(modelCommandParams.size());
        for (BaseParam baseParam: modelCommandParams){
            String paramLabel = baseParam.getParamLabel();
            Object o = params.get(paramLabel);
            if (Objects.nonNull(o)){
                toDeviceParam.put(paramLabel,o);
            }
        }
        toDeviceParam.put("reqId",reqId);
        ToDeviceCommandTransportData toDeviceCommandData = new ToDeviceCommandTransportData(ToDeviceType.COMMAND);
        toDeviceCommandData.setCommandLabel(modelCommand.getCommandLabel());
        toDeviceCommandData.setModelId(modelCommand.getProductModelId());
        toDeviceCommandData.setParams(toDeviceParam);
        String payload = null;
        doCommandLog(deviceId, JsonUtil.toJSONString(toDeviceCommandData),true);
        if (DataFormat.BINARY.equals(product.getDataFormat())){
            try {
                Object o = jsInvokeService.invokeEncodeFunction(productId, toDeviceCommandData);
                payload = HexTool.encodeHexStr((byte[]) o);
            }catch (Exception e){
                doCommandLog(deviceId, e.getMessage(),false);
                return;
            }
        }else {

            payload = JsonUtil.toJSONString(toDeviceCommandData);
        }
        doCommandLog(deviceId, payload,true);
        DeviceCommandSendEntity deviceCommandSendEntity = new DeviceCommandSendEntity();
        deviceCommandSendEntity.setDeviceId(deviceId);
        deviceCommandSendEntity.setCommandId(commandId);
        deviceCommandSendEntity.setCommandLabel(modelCommand.getCommandLabel());
        deviceCommandSendEntity.setParams(payload);
        deviceCommandSendEntity.setTs(Instant.now().toEpochMilli());
        deviceCommandSendEntity.setReqId(reqId);
        deviceCommandSendEntity.setSendStatus(SendStatus.SEND);
        deviceCommandSendService.save(deviceCommandSendEntity);
        ToDeviceControlEvent toDeviceControlEvent = new ToDeviceControlEvent(new SessionId(deviceId, productId), payload);
        toDeviceControlEvent.setSendId(reqId);
        eventPublisher.publish2ControlReqEvent(toDeviceControlEvent);
    }


    @Override
    public void processCloseConnectDevice(String deviceId, String productId) {
        eventPublisher.publishDeviceCloseConnectEvent(new ToDeviceDisConnectEvent(new SessionId(deviceId,productId)));
    }

    private void doCommandLog(String deviceId, String businessDetails, Boolean messageStatus){
        deviceLogProcessor.doPlatform2DeviceLLog(deviceId, BusinessStep.PLATFORM_SEND_COMMAND,businessDetails,messageStatus);
    }




    @Override
    public void processFakeDeviceMessage(DeviceEntity deviceEntity, ProductEntity product, String message) {
        eventPublisher.publishPropertyUpEvent(deviceEntity.getProductId(),deviceEntity.getId(),product.getDataFormat(),message);
    }



    @Autowired
    public void setDeviceCommandSendService(IDeviceCommandSendService deviceCommandSendService) {
        this.deviceCommandSendService = deviceCommandSendService;
    }



    @Autowired
    public void setModelCommandService(IModelCommandService modelCommandService) {
        this.modelCommandService = modelCommandService;
    }


    @Autowired
    public void setProductService(IProductService productService) {
        this.productService = productService;
    }

    @Autowired
    public void setJsInvokeService(JsInvokeService jsInvokeService) {
        this.jsInvokeService = jsInvokeService;
    }


    @Autowired
    public void setDeviceLogProcessor(DeviceLogProcessor deviceLogProcessor) {
        this.deviceLogProcessor = deviceLogProcessor;
    }

    @Autowired
    public void setDeviceConfigSendService(IDeviceConfigSendService deviceConfigSendService) {
        this.deviceConfigSendService = deviceConfigSendService;
    }

    @Autowired
    public void setEventPublisher(IotDmEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

}
