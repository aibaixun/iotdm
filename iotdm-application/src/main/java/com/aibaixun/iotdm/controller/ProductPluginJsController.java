package com.aibaixun.iotdm.controller;

import com.aibaixun.basic.exception.BaseException;
import com.aibaixun.basic.result.JsonResult;
import com.aibaixun.basic.util.HexTool;
import com.aibaixun.common.util.JsonUtil;
import com.aibaixun.iotdm.data.JsDebugParam;
import com.aibaixun.iotdm.entity.ProductPluginJsEntity;
import com.aibaixun.iotdm.script.DefaultJsInvokeService;
import com.aibaixun.iotdm.service.IProductPluginJsService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * js 插件开发 Web Api
 * @author wangxiao@aibaixun.com
 * @date 2022/3/3
 */
@RestController
@RequestMapping("js-plugin")
public class ProductPluginJsController extends BaseController{

    private IProductPluginJsService productPluginJsService;


    private DefaultJsInvokeService defaultJsInvokeService;


    @GetMapping
    public JsonResult<ProductPluginJsEntity> queryJsPlugin (@RequestParam String productId) throws BaseException {
        checkParameterValue(productId,"产品id不允许为空");
        ProductPluginJsEntity pluginJs = productPluginJsService.queryByProductPluginJs(productId);
        return JsonResult.success(pluginJs);
    }


    @PostMapping
    public JsonResult<Boolean> createOrUpdateJsPlugin (@RequestBody @Valid ProductPluginJsEntity productPluginJsEntity){
        String id = productPluginJsEntity.getId();
        boolean result ;
        productPluginJsService.removeJsPlugin(productPluginJsEntity.getProductId());
        if (StringUtils.isBlank(id)){
            result = productPluginJsService.save(productPluginJsEntity);
        }else {
            result = productPluginJsService.updateById(productPluginJsEntity);
        }
        try {
            defaultJsInvokeService.eval(productPluginJsEntity.getProductId(), productPluginJsEntity.getJsScriptBody());
        }catch (Exception e){
            return JsonResult.failed(e.getMessage());
        }
        return JsonResult.success(result);
    }



    @PutMapping
    public JsonResult<Boolean> uninstallJsPlugin (@RequestParam String productId) {
        Boolean uninstallRes = productPluginJsService.uninstallJsPlugin(productId);
        try {
            defaultJsInvokeService.release(productId);
        }catch (Exception e){
            return JsonResult.failed(e.getMessage());
        }
        return JsonResult.success(uninstallRes);
    }


    @PostMapping("/debug")
    public JsonResult<String> debugJsPlugin(@RequestBody @Valid JsDebugParam jsDebugParam) {
        String input = jsDebugParam.getInput();
        Object result = null;
        try {
            if (jsDebugParam.isDecode()){
                byte[] bytes = HexTool.decodeHex(input);
                result = defaultJsInvokeService.testDecode(jsDebugParam.getJsScriptBody(),bytes,jsDebugParam.getTopic());
            }else {
                result = defaultJsInvokeService.testEncode(jsDebugParam.getJsScriptBody(),jsDebugParam.getInput(),jsDebugParam.getTopic());
            }
            return JsonResult.success(JsonUtil.toJSONString(result));
        }catch (Exception e){
            return JsonResult.failed(e.getMessage());
        }
    }




    @Autowired
    public void setProductPluginJsService(IProductPluginJsService productPluginJsService) {
        this.productPluginJsService = productPluginJsService;
    }

    @Autowired
    public void setDefaultJsInvokeService(DefaultJsInvokeService defaultJsInvokeService) {
        this.defaultJsInvokeService = defaultJsInvokeService;
    }

}
