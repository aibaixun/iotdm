package com.aibaixun.iotdm.service;

import com.aibaixun.iotdm.msg.DeviceAuthSecretReqMsg;
import com.aibaixun.iotdm.msg.DeviceInfo;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * 设备信息 服务类
 * @author wangxiao@aibaixun.com
 * @date 2022/3/8
 */
public interface DeviceInfoServer {


    /**
     * mqtt 密钥验证
     * @param deviceAuthSecretReqMsg 设备密钥请求
     * @return 设备信息
     */
    ListenableFuture<DeviceInfo> mqttDeviceAuthBySecret(DeviceAuthSecretReqMsg deviceAuthSecretReqMsg);


    /**
     * 修改设备装改为 在线
     * @param deviceId 设别id
     * @return 修改后结果
     */
    ListenableFuture<Boolean> setDeviceStatus2OnLine(String deviceId);



    /**
     * 修改设备装改为 离线
     * @param deviceId 设别id
     * @param lastConnectTime 连接时间
     * @param lastActivityTime 活跃时间
     * @param hostName 活跃地址
     * @return 修改后结果
     */
    ListenableFuture<Boolean> setDeviceStatus2OffOnLine(String deviceId,Long lastConnectTime,Long lastActivityTime,String hostName);



    /**
     * 修改设备装改为 warn
     * @param deviceId 设别id
     * @return 修改后结果
     */
    ListenableFuture<Boolean> setDeviceStatus2Warn(String deviceId);


    /**
     * 命令 已经到达设备
     * @param deviceId 设备id
     * @param msgId 消息id
     * @return 修改后结果
     */
    ListenableFuture<Boolean> toDeviceMessageIsReceived(String deviceId,int msgId);

    /**
     * redis key 过期
     * @param redisKey redis key
     */
    void onRedisExpirationMessage(String redisKey);


    /**
     * 设置 命令发送id
     * @param sendId send id
     * @param msgId 消息id
     */
    void setControlMsgId(Integer sendId,Integer msgId);

}

