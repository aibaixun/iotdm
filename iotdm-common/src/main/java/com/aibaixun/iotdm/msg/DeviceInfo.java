package com.aibaixun.iotdm.msg;

import com.aibaixun.iotdm.enums.DataFormat;
import com.aibaixun.iotdm.enums.NodeType;
import com.aibaixun.iotdm.enums.ProtocolType;

import java.io.Serializable;
import java.util.UUID;

/**
 * 设备信息
 * @author wangxiao@aibaixun.com
 * @date 2022/3/8
 */
public class DeviceInfo implements Serializable {

    private String deviceCode;

    private UUID sessionId;

    private String deviceId;

    private String productId;

    private ProtocolType protocolType;

    private NodeType nodeType;

    private DataFormat dataFormat;

    private String tenantId;


    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public ProtocolType getProtocolType() {
        return protocolType;
    }

    public void setProtocolType(ProtocolType protocolType) {
        this.protocolType = protocolType;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public void setNodeType(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    public DataFormat getDataFormat() {
        return dataFormat;
    }

    public void setDataFormat(DataFormat dataFormat) {
        this.dataFormat = dataFormat;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public void setDeviceCode(String deviceCode) {
        this.deviceCode = deviceCode;
    }
}
