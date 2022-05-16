package com.aibaixun.iotdm.business.plugin;

import com.aibaixun.common.util.JsonUtil;
import com.aibaixun.iotdm.enums.DataFormat;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * json 字符串 处理
 * <p>并不是插件函数，json 格式处理类</p>
 * @author wang xiao
 * @date 2022/5/16
 */
@Component
public class JsonStrPluginProcessor implements PluginProcessor{

    @Override
    public JsonNode processPluginMethod(String payload, String productId, String topic) {
        return  JsonUtil.parse(payload);
    }

    @Override
    @PostConstruct
    public void add2Factory() {
        PluginFactory.addPluginProcessor(DataFormat.JSON,this);
    }
}
