package com.aibaixun.iotdm.business.plugin;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author wang xiao
 * @date 2022/5/16
 */
public interface PluginProcessor {

    /**
     * 执行plugin 函数
     * @param payload 负载内容
     * @param productId 产品id
     * @param topic 主题
     * @return json node
     */
    JsonNode processPluginMethod(String payload, String productId, String topic);

    /**
     * 添加到 工厂中
     */
    void add2Factory();
}
