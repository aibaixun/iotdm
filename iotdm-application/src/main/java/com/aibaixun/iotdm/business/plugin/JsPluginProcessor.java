package com.aibaixun.iotdm.business.plugin;

import com.aibaixun.basic.util.HexTool;
import com.aibaixun.common.util.JsonUtil;
import com.aibaixun.iotdm.enums.DataFormat;
import com.aibaixun.iotdm.script.JsInvokeService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.script.ScriptException;

/**
 * js 插件处理函数
 * @author wang xiao
 * @date 2022/5/16
 */
@Component
public class JsPluginProcessor implements PluginProcessor{

    private JsInvokeService jsInvokeService;

    @Override
    public JsonNode processPluginMethod(String payload, String productId, String topic) {
        byte [] messageBytes = HexTool.decodeHex(payload);
        try {
            var jsResult = (String)jsInvokeService.invokeDecodeFunction(productId, messageBytes, topic);
            return JsonUtil.parse(jsResult);
        }catch (ScriptException | NoSuchMethodException e){
            return null;
        }
    }

    @Autowired
    public void setJsInvokeService(JsInvokeService jsInvokeService) {
        this.jsInvokeService = jsInvokeService;
    }


    @Override
    @PostConstruct
    public void add2Factory() {
        PluginFactory.addPluginProcessor(DataFormat.BINARY,this);
    }
}
