package com.aibaixun.iotdm.msg;

import java.io.Serializable;

/**
 * 时序数据 实体类
 * @author wangxiao@aibaixun.com
 * @date 2022/3/11
 */
public class TsData implements Serializable {

    private Long ts;

    private String tsLabel;

    private Object tsValue;


    public TsData(Long ts,  String tsLabel, Object tsValue) {
        this.ts = ts;
        this.tsLabel = tsLabel;
        this.tsValue = tsValue;
    }

    public TsData() {
    }

    public Long getTs() {
        return ts;
    }

    public void setTs(Long ts) {
        this.ts = ts;
    }


    public String getTsLabel() {
        return tsLabel;
    }

    public void setTsLabel(String tsLabel) {
        this.tsLabel = tsLabel;
    }

    public Object getTsValue() {
        return tsValue;
    }

    public void setTsValue(Object tsValue) {
        this.tsValue = tsValue;
    }

    @Override
    public String toString() {
        return "TsData{" +
                "ts=" + ts +
                ", tsLabel='" + tsLabel + '\'' +
                ", tsValue='" + tsValue + '\'' +
                '}';
    }
}
