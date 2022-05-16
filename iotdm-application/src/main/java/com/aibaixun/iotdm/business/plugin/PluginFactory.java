package com.aibaixun.iotdm.business.plugin;

import com.aibaixun.iotdm.enums.DataFormat;

import java.util.HashMap;
import java.util.Map;

/**
 *  工厂方法 后续需要扩展 再次改动
 * @author wang xiao
 * @date 2022/5/16
 */
public class PluginFactory {

    private PluginFactory (){
    }


    private static final Map<DataFormat,PluginProcessor> PLUGIN_FACTORY = new HashMap<>(8);


    public static PluginProcessor getPluginProcessor (DataFormat dateFormat){
        return PLUGIN_FACTORY.get(dateFormat);
    }

    public static PluginProcessor addPluginProcessor (DataFormat dateFormat,PluginProcessor pluginProcessor){
        return PLUGIN_FACTORY.put(dateFormat,pluginProcessor);
    }
}
