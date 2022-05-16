package com.aibaixun.iotdm.business;

import com.aibaixun.iotdm.business.plugin.PluginFactory;
import com.aibaixun.iotdm.constants.TopicConstants;
import com.aibaixun.iotdm.enums.BusinessStep;
import com.aibaixun.iotdm.enums.DataFormat;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 插件 业务处理
 * @author Wang Xiao
 * @date 2022/3/30
 */
@Component
public class PlugBusinessProcessor extends AbstractBusinessProcessor{



    private MatchBusinessProcessor matchBusinessProcessor;


    public void processReport(DataFormat dataFormat,String payload,String deviceId,String productId,boolean property){
        logD2P(deviceId, BusinessStep.DEVICE_REPORT_DATA,payload,true);
        JsonNode jsonNode= null;
        try {
            jsonNode = invokePluginMethod(dataFormat,payload,productId, property?TopicConstants.PROPERTIES_UP:TopicConstants.MESSAGE_UP);
            logD2P(deviceId,  BusinessStep.PLATFORM_RESOLVING_DATA,jsonNode !=null?jsonNode.toString():"{}",true);
        }catch (Exception e){
            logger.error("PlugBusinessProcessor.processReport >> is error ,product is:{},device is :{},payload is:{},error is:{}",productId,deviceId,payload,e.getMessage());
            logD2P(deviceId, BusinessStep.DEVICE_REPORT_DATA,e.getMessage(),false);
        }
        boolean jsonNodeEmpty = Objects.isNull(jsonNode);
        if (jsonNodeEmpty){
            logger.warn("PlugBusinessProcessor.processReport >> jsonNode is empty ,product is:{},device is :{}",productId,deviceId);
        }
        MetaData metaData = new MetaData(deviceId, productId);
        if (property){
            PrePropertyBusinessMsg propertyBusinessMsg = new PrePropertyBusinessMsg(metaData, jsonNode);
            matchBusinessProcessor.processProperty(propertyBusinessMsg);
        }else {
            MessageBusinessMsg messageBusinessMsg = new MessageBusinessMsg(metaData, !jsonNodeEmpty ? jsonNode : payload);
            matchBusinessProcessor.processMessage(messageBusinessMsg);
        }
    }


    /**
     * 执行插件函数
     * @param dataFormat 数据格式
     * @param payload 负载内容
     * @param productId 产品id
     * @param topic 主题
     */
    public JsonNode doInvokePluginMethod (DataFormat dataFormat,String payload,String productId,String topic) {
        JsonNode jsonNode;
        try {
            jsonNode = invokePluginMethod(dataFormat,payload, productId, topic);
            return jsonNode;
        }catch (Exception e){
            logger.error("PlugBusinessProcessor.doInvokePluginMethod >> is error ,product is:{},payload is:{},error is:{}",productId,payload,e.getMessage());
        }
        return null;
    }



    /**
     * 执行插件函数
     * @param dataFormat 数据格式
     * @param payload 负载内容
     * @param productId 产品id
     * @param topic 主题
     */
    private JsonNode invokePluginMethod (DataFormat dataFormat,String payload,String productId,String topic) {
        return PluginFactory.getPluginProcessor(dataFormat).processPluginMethod(payload,productId,topic);
    }




    @Autowired
    public void setMatchBusinessProcess(MatchBusinessProcessor matchBusinessProcessor) {
        this.matchBusinessProcessor = matchBusinessProcessor;
    }
}
