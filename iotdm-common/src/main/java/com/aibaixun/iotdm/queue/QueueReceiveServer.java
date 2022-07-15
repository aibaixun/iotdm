package com.aibaixun.iotdm.queue;

import com.aibaixun.iotdm.business.MessageBusinessMsg;
import com.aibaixun.iotdm.business.PostPropertyBusinessMsg;
import com.aibaixun.iotdm.event.DeviceSessionEvent;
import com.aibaixun.iotdm.event.EntityChangeEvent;
import org.springframework.messaging.Message;

/**
 * @author wangxiao@aibaixun.com
 * @date 2022/3/11
 */
public interface QueueReceiveServer {

    /**
     * 接受 消息队列的时序数据
     * @param tsData 数据
     */
     void receivePropertyTsData(Message<PostPropertyBusinessMsg> tsData);


    /**
     * 接受 消息队列的时序数据
     * @param tsData 数据
     */
    void receiveMessageTsData(Message<MessageBusinessMsg> tsData);



    /**
     * 接受 消息队列session数据
     * @param sessionData 数据
     */
     void receiveSessionData(Message<DeviceSessionEvent> sessionData);



    /**
     * 接受 实体数据数据
     * @param entityData 数据
     */
     void receiveEntityData(Message<EntityChangeEvent> entityData);
}
