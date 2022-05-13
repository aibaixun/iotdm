package com.aibaixun.iotdm.enums;

import com.aibaixun.common.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 数据类型
 * @author wangxiao@aibaixun.com
 * @date 2022/3/3
 */
public enum DataType {

    /**
     * int
     */
    INT{
        @Override
        public Integer parseJsonNode(JsonNode jsonNode) {
            return jsonNode.asInt();
        }
    },

    /**
     * string
     */
    STR{
        @Override
        public String parseJsonNode(JsonNode jsonNode) {
            return jsonNode.asText();
        }
    },
    /**
     * double
     */
    DECIMAL{
        @Override
        public Double parseJsonNode(JsonNode jsonNode) {
            return jsonNode.asDouble();
        }
    },
    /**
     * list
     */
    STR_LIST{
        @Override
        public List<String> parseJsonNode(JsonNode jsonNode) {
            return  JsonUtil.toList(jsonNode.textValue());
        }
    },
    /**
     * list
     */
    INT_LIST{
        @Override
        public List<Integer> parseJsonNode(JsonNode jsonNode) {
            return  JsonUtil.toList(jsonNode.textValue());
        }
    },
    /**
     * str json
     */
    JSON{
        @Override
        public String parseJsonNode(JsonNode jsonNode) {
            return jsonNode.textValue();
        }
    };


    public abstract Object parseJsonNode(JsonNode jsonNode);


}