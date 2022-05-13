package com.aibaixun.iotdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.Instant;


/**
 * <p>
 * 设备属性上报记录表 
 * </p>
 *
 * @author baixun
 * @since 2022-03-03
 */
@TableName("t_device_property_report")
public class DevicePropertyReportEntity implements Serializable {

    private static final long serialVersionUID = 19876543210L;



    /**
     * 设备id
     */
    private String deviceId;

    /**
     * 属性id
     */
    private String propertyId;

    /**
     * 冗余字段
     */
    private String propertyLabel;

    /**
     * 属性值
     */
    private Object propertyValue;

    /**
     * 时间
     */
    private Long ts;


    public DevicePropertyReportEntity() {
    }


    public DevicePropertyReportEntity(String deviceId, String propertyId, Object propertyValue,String propertyLabel) {
        this.deviceId = deviceId;
        this.propertyId = propertyId;
        this.propertyValue = propertyValue;
        this.propertyLabel = propertyLabel;
        this.ts = Instant.now().toEpochMilli();
    }

    public String getPropertyLabel() {
        return propertyLabel;
    }

    public void setPropertyLabel(String propertyLabel) {
        this.propertyLabel = propertyLabel;
    }


    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(String propertyId) {
        this.propertyId = propertyId;
    }

    public Object getPropertyValue() {
        return propertyValue;
    }

    public void setPropertyValue(Object propertyValue) {
        this.propertyValue = propertyValue;
    }

    public Long getTs() {
        return ts;
    }

    public void setTs(Long ts) {
        this.ts = ts;
    }



    @Override
    public String toString() {
        return "DevicePropertyReport{" +
        ", deviceId=" + deviceId +
        ", propertyId=" + propertyId +
        ", propertyValue=" + propertyValue +
        ", ts=" + ts +
        "}";
    }
}
