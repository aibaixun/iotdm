package com.aibaixun.iotdm.business;

import com.aibaixun.common.util.JsonUtil;
import com.aibaixun.iotdm.data.ProductEntityInfo;
import com.aibaixun.iotdm.data.ProductModelEntityInfo;
import com.aibaixun.iotdm.entity.DevicePropertyReportEntity;
import com.aibaixun.iotdm.entity.ModelPropertyEntity;
import com.aibaixun.iotdm.enums.BusinessStep;
import com.aibaixun.iotdm.msg.TsData;
import com.aibaixun.iotdm.service.IDevicePropertyReportService;
import com.aibaixun.iotdm.service.IProductService;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 物模型匹配处理类
 * @author Wang Xiao
 * @date 2022/3/30
 */
@Component
public class MatchBusinessProcessor extends AbstractBusinessProcessor{

    private final String modelLabelKey = "modelId";

    private QueueBusinessProcessor queueBusinessProcessor;

    private IDevicePropertyReportService propertyReportService;

    private IProductService productService;

    private  final ExpressionParser expressionParser = new SpelExpressionParser();

    private  final ThreadLocal<EvaluationContext> contextLocals= new InheritableThreadLocal<>();

    public void processProperty(PrePropertyBusinessMsg prePropertyBusinessMsg){
        String productId = prePropertyBusinessMsg.getMetaData().getProductId();
        String deviceId = prePropertyBusinessMsg.getMetaData().getDeviceId();
        ProductEntityInfo productInfo = productService.queryProductInfoById(productId);
        List<ProductModelEntityInfo> models = productInfo.models;
        if (CollectionUtils.isEmpty(models)){
            logD2P(deviceId,  BusinessStep.MATCH_MODEL,"product model is empty",false);
            return;
        }
        JsonNode prePropertyBusinessMsgPropertyJsonNode = prePropertyBusinessMsg.getPropertyJsonNode();
        if (Objects.isNull(prePropertyBusinessMsgPropertyJsonNode)){
            logD2P(deviceId,  BusinessStep.MATCH_MODEL,"resolving data is empty",false);
            return;
        }
        List<ModelPropertyEntity> modelPropertyEntities = new ArrayList<>(models.size()*3);
        for (ProductModelEntityInfo model : models) {
            modelPropertyEntities.addAll(model.getProperties());
        }
        int size = prePropertyBusinessMsgPropertyJsonNode.size();
        List<DevicePropertyReportEntity> reportEntities = new ArrayList<>(size);
        try {
            if (prePropertyBusinessMsgPropertyJsonNode.isArray()){
                for (int i = 0; i < size; i++) {
                    JsonNode jsonNode = prePropertyBusinessMsgPropertyJsonNode.get(i);
                    reportEntities.addAll(doMatchPropertyAnd2DbEntity(deviceId,jsonNode,models,modelPropertyEntities));
                }
            }else {
                reportEntities.addAll(doMatchPropertyAnd2DbEntity(deviceId,prePropertyBusinessMsgPropertyJsonNode,models,modelPropertyEntities));
            }
        }catch (Exception e){
            logD2P(deviceId, BusinessStep.MATCH_MODEL,"Match Model property is empty"+e.getMessage(),false);
        }

        propertyReportService.saveOrUpdateBatchDeviceProperties(deviceId,reportEntities);
        logD2P(deviceId, BusinessStep.MATCH_MODEL, JsonUtil.toJSONString(reportEntities),true);
        queueBusinessProcessor.processProperty2Mq(new PostPropertyBusinessMsg(prePropertyBusinessMsg.getMetaData(),toTsData(reportEntities)));
    }

    public void  processMessage(MessageBusinessMsg messageBusinessMsg){
        queueBusinessProcessor.processMessage2Mq(messageBusinessMsg);
    }




    private List<DevicePropertyReportEntity> doMatchPropertyAnd2DbEntity(String deviceId,JsonNode jsonNode,List<ProductModelEntityInfo> modelEntityInfos,List<ModelPropertyEntity> modelPropertyEntities){
        String modelLabel = null;
        if (jsonNode.has(modelLabelKey)){
            modelLabel = jsonNode.get(modelLabelKey).asText("");
        }
        int size = jsonNode.size();
        contextLocals.set(new StandardEvaluationContext());
        List<DevicePropertyReportEntity> results = new ArrayList<>(size);
        Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
        while (fields.hasNext()){
            Map.Entry<String, JsonNode> next = fields.next();
            List<ModelPropertyEntity> matchProperty = matchProperty(modelLabel,next.getKey(), modelEntityInfos, modelPropertyEntities);
            change2DbEntity(deviceId, next.getValue(), matchProperty,results);
        }
        contextLocals.remove();
        return results;
    }


    private List<ModelPropertyEntity> matchProperty(String modelLabel,String key,List<ProductModelEntityInfo> modelEntityInfos,List<ModelPropertyEntity> modelPropertyEntities){
        if (StringUtils.equals(modelLabelKey,key)) {
            return null;
        }
        if (StringUtils.isBlank(modelLabel)){
            return   modelPropertyEntities.stream().filter(e->StringUtils.equals(e.getPropertyLabel(),key)).collect(Collectors.toList());
        }
        ProductModelEntityInfo first = modelEntityInfos.stream().filter(e -> StringUtils.equals(e.getModelLabel(), modelLabel)).findAny().orElse(null);
        if (Objects.isNull(first)){
            return null;
        }
        return first.getProperties().stream().filter(e-> StringUtils.equals(e.getPropertyLabel(),key)).collect(Collectors.toList());
    }


    public void change2DbEntity(String deviceId,JsonNode jsonNode,List<ModelPropertyEntity> matchProperties,List<DevicePropertyReportEntity> reportEntities){
        if (CollectionUtils.isEmpty(matchProperties)){
            return;
        }
        for (ModelPropertyEntity modelPropertyEntity : matchProperties) {
            String id = modelPropertyEntity.getId();
            String label = modelPropertyEntity.getPropertyLabel();
            String value = spElExpression(label,jsonNode.asText(),modelPropertyEntity.getExpression());
            reportEntities.add(new DevicePropertyReportEntity(deviceId,id,value,label));
        }
    }


    private List<TsData> toTsData (List<DevicePropertyReportEntity> devicePropertyReportEntities) {
        List<TsData> result = new ArrayList<>();
        for (DevicePropertyReportEntity entity : devicePropertyReportEntities) {
            result.add(new TsData(entity.getTs(), entity.getPropertyLabel(),entity.getPropertyValue()));
        }
        return result;
    }


    private String spElExpression(String propertyLabel,String propertyValue,String expression){
        if (StringUtils.isEmpty(expression)){
            return propertyValue;
        }
        Expression spElExpression = expressionParser.parseExpression(expression);
        EvaluationContext evaluationContext = contextLocals.get();
        evaluationContext.setVariable(propertyLabel,propertyValue);
        try {
            return String.valueOf(spElExpression.getValue());
        }catch (Exception e){
            return propertyValue;
        }
    }


    @Autowired
    public void setQueueBusinessProcessor(QueueBusinessProcessor queueBusinessProcessor) {
        this.queueBusinessProcessor = queueBusinessProcessor;
    }


    @Autowired
    public void setPropertyReportService(IDevicePropertyReportService propertyReportService) {
        this.propertyReportService = propertyReportService;
    }

    @Autowired
    public void setProductService(IProductService productService) {
        this.productService = productService;
    }




}
