package com.aibaixun.iotdm.transport.mqtt;

import com.aibaixun.basic.util.HexTool;
import com.aibaixun.iotdm.enums.DataFormat;
import com.aibaixun.iotdm.enums.ProtocolType;
import com.aibaixun.iotdm.msg.*;
import com.aibaixun.iotdm.transport.*;
import com.aibaixun.iotdm.transport.mqtt.session.DeviceSessionCtx;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.aibaixun.iotdm.constants.TopicConstants.*;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_ACCEPTED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;
import static io.netty.handler.codec.mqtt.MqttMessageType.*;
import static io.netty.handler.codec.mqtt.MqttQoS.*;


/**
 * Mqtt transport handler
 * @author wangxiao@aibaixun.com
 * @date 2022/3/8
 */
public class MqttTransportHandler extends ChannelInboundHandlerAdapter implements GenericFutureListener<Future<? super Void>>, TransportSessionListener {

    private static final ByteBufAllocator ALLOCATOR = new UnpooledByteBufAllocator(false);

    private final Logger log  = LoggerFactory.getLogger("mqtt-transport");

    /**
     * handler id
     */
    private final UUID handlerId;

    /**
     * 上下文
     */
    private final MqttTransportContext context;

    /**
     * transport service
     */
    private final TransportServer transportService;

    /**
     * 设备session
     */
    private final DeviceSessionCtx deviceSessionCtx;

    /**
     * 地址
     */
    volatile InetSocketAddress address;



    MqttTransportHandler(MqttTransportContext context) {
        this.handlerId = UUID.randomUUID();
        this.context = context;
        this.transportService = context.getTransportService();
        this.deviceSessionCtx = new DeviceSessionCtx(context);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext channelHandlerContext) throws Exception {
        super.channelRegistered(channelHandlerContext);
        context.channelRegistered();
        deviceSessionCtx.setChannel(channelHandlerContext);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext channelHandlerContext) throws Exception {
        super.channelUnregistered(channelHandlerContext);
        context.channelUnregistered();
    }

    @Override
    public void channelRead(ChannelHandlerContext channelHandlerContext, Object msg) {
        log.trace("MqttTransportHandler.channelRead >> [{}] Processing msg: {}", handlerId, msg);
        if (address == null) {
            address = getAddress(channelHandlerContext);
        }
        try {
            if (msg instanceof MqttMessage) {
                MqttMessage message = (MqttMessage) msg;
                if (message.decoderResult().isSuccess()) {
                    processMqttMsg(channelHandlerContext, message);
                } else {
                    log.error("MqttTransportHandler.channelRead >> [{}] Message decoding failed: {}", handlerId, message.decoderResult().cause().getMessage());
                    channelHandlerContext.close();
                }
            } else {
                log.debug("MqttTransportHandler.channelRead >> [{}] Received non mqtt message: {}", handlerId, msg.getClass().getSimpleName());
                channelHandlerContext.close();
            }
        } finally {
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext channelHandlerContext) {
        channelHandlerContext.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext channelHandlerContext, Throwable cause) {
        log.error("MqttTransportHandler.exceptionCaught >> [{}] Unexpected Exception", handlerId, cause);
        deviceSessionCtx.setDisconnected();
        channelHandlerContext.close();
        if (cause instanceof OutOfMemoryError) {
            log.error("MqttTransportHandler.exceptionCaught >> Received critical error： OutOfMemoryError. Going to shutdown the service.");
            System.exit(1);
        }
    }

    @Override
    public void operationComplete(Future<? super Void> future) {
        log.trace("MqttTransportHandler.operationComplete >> [{}] Channel closed!", handlerId);
        doDisconnect();
    }

    /**
     * 处理mqtt 消息
     * @param channelHandlerContext ctx
     * @param msg mqtt 消息
     */
    void processMqttMsg(ChannelHandlerContext channelHandlerContext, MqttMessage msg) {
        if (msg.fixedHeader() == null) {
            log.info("MqttTransportHandler.processMqttMsg >> [{}:{}] Invalid message received", address.getHostName(), address.getPort());
            channelHandlerContext.close();
            return;
        }
        deviceSessionCtx.setChannel(channelHandlerContext);
        if (CONNECT.equals(msg.fixedHeader().messageType())) {
            processConnect(channelHandlerContext, (MqttConnectMessage) msg);
        }else {
            processSessionMsg(channelHandlerContext,msg);
        }
    }

    /**
     * 获取地址
     * @param channelHandlerContext  ctx
     * @return 地址
     */
    private InetSocketAddress getAddress(ChannelHandlerContext channelHandlerContext) {
        var address = channelHandlerContext.channel().attr(MqttTransportService.ADDRESS).get();
        if (address == null) {
            log.trace("MqttTransportHandler.getAddress >> [{}] Received empty address.", channelHandlerContext.channel().id());
            InetSocketAddress remoteAddress = (InetSocketAddress) channelHandlerContext.channel().remoteAddress();
            log.trace("MqttTransportHandler.getAddress >> [{}] Going to use address: {}", channelHandlerContext.channel().id(), remoteAddress);
            return remoteAddress;
        } else {
            log.trace("MqttTransportHandler.getAddress >> [{}] Received address: {}", channelHandlerContext.channel().id(), address);
        }
        return address;
    }

    /**
     * 处理连接消息
     * @param channelHandlerContext  ctx
     * @param mqttConnectMessage 连接报文
     */
    private void processConnect(ChannelHandlerContext channelHandlerContext,MqttConnectMessage mqttConnectMessage){
        String clientId = mqttConnectMessage.payload().clientIdentifier();
        log.debug("MqttTransportHandler.processConnect >> [{}][{}] Processing connect msg for client: {}!", address, handlerId, clientId);
        processAuthSecretConnect(channelHandlerContext, clientId,mqttConnectMessage);
    }

    /**
     * 设备密钥连接
     * @param channelHandlerContext ctx
     * @param connectMessage 连接报文
     */
    private void processAuthSecretConnect(ChannelHandlerContext channelHandlerContext,String clientId, MqttConnectMessage connectMessage) {
        String userName = connectMessage.payload().userName();
        byte[] passwordBytes = connectMessage.payload().passwordInBytes();
        String password = Objects.nonNull(passwordBytes)?new String(passwordBytes, CharsetUtil.UTF_8):"";
        log.debug("MqttTransportHandler.processAuthSecretConnect >> [{}][{}] Processing connect msg for client with clientId: {} ,username: {} ,password:{}!", address, handlerId, clientId,userName,password);
        transportService.processDeviceAuthBySecret(ProtocolType.MQTT, new DeviceAuthSecretReqMsg(clientId, userName, password), new TransportServiceCallback<>() {
            @Override
            public void onSuccess(DeviceAuthRespMsg msg) {
                processDeviceAuthResponse(msg,channelHandlerContext,connectMessage);
            }
            @Override
            public void onError(Throwable e) {
                MqttConnectReturnCode mqttConnectReturnCode = MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE;
                if (e instanceof MqttTransportException){
                    mqttConnectReturnCode = MqttConnectReturnCode.valueOf(((MqttTransportException) e).getReturnCode());
                }
                log.trace("MqttTransportHandler.processAuthSecretConnect [{}] [{}] Failed to process with clientId: {} ,username: {} ,password:{},error msg :{}", address, handlerId, clientId, userName, password, e.getMessage());
                channelHandlerContext.writeAndFlush(createMqttConnAckMsg(mqttConnectReturnCode, connectMessage));
                channelHandlerContext.close();
            }
        });
    }

    private MqttConnAckMessage createMqttConnAckMsg(MqttConnectReturnCode returnCode, MqttConnectMessage msg) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(CONNACK, false, AT_MOST_ONCE, false, 0);
        MqttConnAckVariableHeader mqttConnAckVariableHeader =
                new MqttConnAckVariableHeader(returnCode, !msg.variableHeader().isCleanSession());
        return new MqttConnAckMessage(mqttFixedHeader, mqttConnAckVariableHeader);
    }

    /**
     * 处理授权返回消息
     * @param respMsg 授权返回消息
     * @param ctx ctx
     * @param connectMessage 连接报文
     */
    private void processDeviceAuthResponse(DeviceAuthRespMsg respMsg, ChannelHandlerContext ctx, MqttConnectMessage connectMessage) {
        if (!respMsg.hasDeviceInfo()) {
            context.onDeviceAuthFailure(address);
            ctx.writeAndFlush(createMqttConnAckMsg(CONNECTION_REFUSED_NOT_AUTHORIZED, connectMessage));
            ctx.close();
        } else {
            context.onDeviceAuthSuccess(address);
            TransportSessionInfo transportSessionInfo = TransportSessionInfoHolder.create(respMsg.getDeviceInfo());
            ctx.writeAndFlush(createMqttConnAckMsg(CONNECTION_ACCEPTED, connectMessage));
            transportService.processDeviceConnectSuccess(transportSessionInfo, new TransportServiceCallback<>() {
                @Override
                public void onSuccess(Boolean msg) {
                    initDeviceSessionCtx(respMsg.getDeviceInfo(),transportSessionInfo.getSessionId());
                    transportService.registerSession(transportSessionInfo, MqttTransportHandler.this);
                }

                @Override
                public void onError(Throwable e) {
                    log.warn("MqttTransportHandler.processDeviceAuthResponse >> [{}] [{}] Failed to submit session event", address, handlerId, e);
                    ctx.writeAndFlush(createMqttConnAckMsg(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE, connectMessage));
                    ctx.close();
                }
            });
        }
    }

    /**
     * 设置设备上下文 信息
     * @param deviceInfo 设备 信息
     */
    private void initDeviceSessionCtx(DeviceInfo deviceInfo, SessionId sessionId) {
        deviceSessionCtx.setConnected();
        deviceSessionCtx.setSessionId(sessionId);
        deviceSessionCtx.setDataFormat(deviceInfo.getDataFormat());
    }

    /**
     * 处理 会话 报文
     * @param channelHandlerContext  ctx
     * @param mqttMessage 数据报文
     */
    private void  processSessionMsg (ChannelHandlerContext channelHandlerContext,MqttMessage mqttMessage){
        MqttMessageType mqttMessageType = mqttMessage.fixedHeader().messageType();
        switch (mqttMessageType){
            case PUBLISH:
                processPublishMsg(channelHandlerContext,(MqttPublishMessage) mqttMessage);
                break;
            case SUBSCRIBE:
                processSubscribeMsg(channelHandlerContext,(MqttSubscribeMessage) mqttMessage);
                break;
            case UNSUBSCRIBE:
                processUnsubscribeMsg(channelHandlerContext,(MqttUnsubscribeMessage) mqttMessage);
                break;
            case PINGREQ:
                processPingMsg(channelHandlerContext);
                break;
            case DISCONNECT:
                processDisConnectMsg(channelHandlerContext);
                break;
            case PUBACK:
                processPubAckMsg((MqttPubAckMessage) mqttMessage);
                break;
            default:
                break;
        }
    }


    /**
     * 处理 发布消息
     * @param channelHandlerContext ctx
     * @param mqttPublishMessage 发布消息
     */
   private void  processPublishMsg (ChannelHandlerContext channelHandlerContext,MqttPublishMessage mqttPublishMessage) {
       if (!checkConnected()) {
           return;
       }
       String topicName = mqttPublishMessage.variableHeader().topicName();
       int msgId = mqttPublishMessage.variableHeader().packetId();
       try {
           switch (topicName){
               case PROPERTIES_UP:
                    processPropertiesUp(channelHandlerContext,mqttPublishMessage,msgId);
                   break;
               case MESSAGE_UP:
                   processMessageUp(channelHandlerContext,mqttPublishMessage,msgId);
                   break;
               case CONFIG_RESP:
                   processConfigRespUp(channelHandlerContext, msgId);
                   break;
               case OTA_RESP:
                   processOtaRespUp(channelHandlerContext,mqttPublishMessage,msgId);
                   break;
               case CONTROL_RESP:
                   processControlRespUp(channelHandlerContext,mqttPublishMessage,msgId);
                   break;
               case CONTROL_REQ:
                   processControlReqUp(channelHandlerContext,mqttPublishMessage,msgId);
                   break;
               case WARN:
                   processWarnUp(channelHandlerContext, msgId);
                   break;
               default:
                   log.warn("MqttTransportHandler.processPublishMsg >> [{}] [{}] Failed topic is error,topic:{}", address, handlerId, topicName);
                   channelHandlerContext.close();
           }
       }catch (RuntimeException e){
           log.warn("MqttTransportHandler.processPublishMsg >> [{}] [{}] error,topic:{},msgId:{}", address, handlerId, topicName,msgId);
           channelHandlerContext.close();
       }
    }



    /**
     * 处理订阅消息
     * @param channelHandlerContext ctx
     * @param mqttSubscribeMessage 订阅消息
     */
    private void processSubscribeMsg(ChannelHandlerContext channelHandlerContext,MqttSubscribeMessage mqttSubscribeMessage){
        if (!checkConnected()){
            return;
        }
        var msgId = mqttSubscribeMessage.variableHeader().messageId();
        log.trace("MqttTransportHandler.processSubscribeMsg >> [{}][{}] Processing subscription [{}]!", handlerId,address, msgId);
        List<Integer> qosList = new ArrayList<>();
        boolean activityReported = false;
        for (MqttTopicSubscription subscription : mqttSubscribeMessage.payload().topicSubscriptions()) {
            String topic = subscription.topicName();
            MqttQoS reqQoS = subscription.qualityOfService();
            try {
                switch (topic) {
                    case PROPERTIES_GET: {
                        deviceSessionCtx.setSubscribeConfig(true);
                        activityReported = true;
                        qosList.add(AT_MOST_ONCE.value());
                        break;
                    }
                    case OTA_REQ: {
                        deviceSessionCtx.setSubscribeOta(true);
                        activityReported = true;
                        qosList.add(AT_MOST_ONCE.value());
                        break;
                    }
                    case CONTROL_REQ: {
                        deviceSessionCtx.setSubscribeControl(true);
                        activityReported = true;
                        qosList.add(AT_MOST_ONCE.value());
                        break;
                    }
                    default:
                        log.warn("MqttTransportHandler.processSubscribeMsg >> [{}][{}] Failed to subscribe to [{}][{}]", handlerId,address, topic, reqQoS);
                        qosList.add(FAILURE.value());
                        break;
                }
            } catch (Exception e) {
                log.warn("MqttTransportHandler.processSubscribeMsg >> [{}][{}] Failed to subscribe to [{}][{}],error:{}", handlerId,address, topic, reqQoS,e.getMessage());
                qosList.add(FAILURE.value());
            }
        }
        if (!activityReported) {
            transportService.reportActivity(deviceSessionCtx.getSessionId());
        }
        channelHandlerContext.writeAndFlush(createSubAckMessage(msgId, qosList));
    }

    /**
     * 处理取消订阅消息
     * @param channelHandlerContext ctx
     * @param mqttUnsubscribeMessage 取消订阅消息
     */
    private void processUnsubscribeMsg(ChannelHandlerContext channelHandlerContext, MqttUnsubscribeMessage mqttUnsubscribeMessage){
        if (!checkConnected()) {
            return;
        }
        boolean activityReported = false;
        int msgId = mqttUnsubscribeMessage.variableHeader().messageId();
        log.trace("MqttTransportHandler.processUnsubscribeMsg >> [{}][{}] Processing subscription [{}]!", handlerId,address,msgId );
        var topics = mqttUnsubscribeMessage.payload().topics();
        for (String topic : topics) {
            try {
                switch (topic) {
                    case PROPERTIES_GET: {
                        deviceSessionCtx.setSubscribeConfig(false);
                        activityReported = true;
                        break;
                    }
                    case OTA_REQ: {
                        deviceSessionCtx.setSubscribeOta(false);
                        activityReported = true;
                        break;
                    }
                    case CONTROL_REQ: {
                        deviceSessionCtx.setSubscribeControl(false);
                        activityReported = true;
                        break;
                    }
                    default:
                        log.warn("MqttTransportHandler.processUnsubscribeMsg >> [{}][{}] Failed to unsubscribe to [{}]", handlerId,address, topic);
                        break;
                }
            } catch (Exception e) {
                log.warn("MqttTransportHandler.processUnsubscribeMsg >> [{}][{}] Failed to unsubscribe to [{}],error:{}", handlerId,address, topic,e.getMessage());
            }
        }
        if (!activityReported) {
            transportService.reportActivity(deviceSessionCtx.getSessionId());
        }
        channelHandlerContext.writeAndFlush(createUnSubAckMessage(msgId));
    }


    /**
     * 处理 ping 报文
     * @param channelHandlerContext ctx
     */
    private void processPingMsg(ChannelHandlerContext channelHandlerContext){
        log.info("MqttTransportHandler.processPingMsg");
        if (checkConnected()) {
            channelHandlerContext.writeAndFlush(new MqttMessage(new MqttFixedHeader(PINGRESP, false, AT_MOST_ONCE, false, 0)));
            transportService.reportActivity(deviceSessionCtx.getSessionId());
        }
    }

    /**
     * 处理断开报文
     * @param channelHandlerContext ctx
     */
    private void  processDisConnectMsg(ChannelHandlerContext channelHandlerContext){
        channelHandlerContext.close();
        String hostName = address.getHostString();
        transportService.processDeviceDisConnect(deviceSessionCtx.getSessionId(),hostName);
    }


    /**
     * 处理 发布 ack
     * @param mqttPubAckMessage 发布消息
     */
    private void  processPubAckMsg(MqttPubAckMessage mqttPubAckMessage){
        int msgId = mqttPubAckMessage.variableHeader().messageId();
        transportService.processPubAck(deviceSessionCtx.getSessionId(), msgId);
    }


    /**
     * 属性处理
     * @param channelHandlerContext ctx
     * @param mqttPublishMessage 发布消息
     * @param msgId 消息id
     */
    private void  processPropertiesUp(ChannelHandlerContext channelHandlerContext,MqttPublishMessage mqttPublishMessage,int msgId) {
        String payload = getPayload(mqttPublishMessage);
        transportService.processPropertyUp(deviceSessionCtx.getSessionId(), deviceSessionCtx.getDataFormat(),payload,
                pubAckCallback(channelHandlerContext,msgId));
        transportService.reportActivity(deviceSessionCtx.getSessionId());
    }

    /**
     * 告警消息
     * @param channelHandlerContext  ctx
     * @param msgId 消息id
     */
    private void  processWarnUp(ChannelHandlerContext channelHandlerContext, int msgId) {
        transportService.processWarnUp(deviceSessionCtx.getSessionId(), pubAckCallback(channelHandlerContext,msgId));
        channelHandlerContext.close();
    }

    /**
     * 消息处理
     * @param channelHandlerContext ctx
     * @param mqttPublishMessage 发布消息
     * @param msgId 消息id
     */
    private void  processMessageUp(ChannelHandlerContext channelHandlerContext,MqttPublishMessage mqttPublishMessage,int msgId) {
        String payload = getHexPayload(mqttPublishMessage.payload());
        transportService.processMessageUp(deviceSessionCtx.getSessionId(),deviceSessionCtx.getDataFormat(),  payload, pubAckCallback(channelHandlerContext,msgId));
        transportService.reportActivity(deviceSessionCtx.getSessionId());
    }

    /**
     * 配置上报反馈
     * @param channelHandlerContext ctx
     * @param msgId 消息 id
     */
    public void processConfigRespUp(ChannelHandlerContext channelHandlerContext, int msgId){
        transportService.processConfigRespUp(deviceSessionCtx.getSessionId(), pubAckCallback(channelHandlerContext,msgId));
    }

    /**
     * OTA 升级
     * @param channelHandlerContext ctx
     * @param mqttPublishMessage mqtt 消息发布
     * @param msgId 消息 id
     */
    public void processOtaRespUp(ChannelHandlerContext channelHandlerContext,MqttPublishMessage mqttPublishMessage,int msgId){
        String payload = getPayload(mqttPublishMessage);
        transportService.processOtaRespUp(deviceSessionCtx.getSessionId(), deviceSessionCtx.getDataFormat(), payload,
                pubAckCallback(channelHandlerContext,msgId));
    }

    /**
     * 命令下发反馈
     * @param channelHandlerContext ctx
     * @param mqttPublishMessage mqtt 消息发布
     * @param msgId 消息 id
     */
    public void processControlRespUp(ChannelHandlerContext channelHandlerContext,MqttPublishMessage mqttPublishMessage,int msgId){
        String payload = getPayload(mqttPublishMessage);
        transportService.processControlRespUp(deviceSessionCtx.getSessionId(),  deviceSessionCtx.getDataFormat(),payload,
                pubAckCallback(channelHandlerContext,msgId));
    }


    /**
     * 命令请求
     * @param channelHandlerContext ctx
     * @param mqttPublishMessage mqtt 消息发布
     * @param msgId 消息 id
     */
    public void processControlReqUp(ChannelHandlerContext channelHandlerContext,MqttPublishMessage mqttPublishMessage,int msgId){
        String payload = getPayload(mqttPublishMessage);
        transportService.processControlReqUp(deviceSessionCtx.getSessionId(),  deviceSessionCtx.getDataFormat(),payload,
                pubAckCallback(channelHandlerContext,msgId));
    }

    /**
     * 获取负载内容
     * @param mqttPublishMessage 发布消息
     * @return 负载字符串
     */
    private String getPayload(MqttPublishMessage mqttPublishMessage) {
        String payload;
        if (deviceSessionCtx.getDataFormat().equals(DataFormat.JSON)){
            payload = getJsonPayload(mqttPublishMessage.payload());
        }else {
            payload = getHexPayload(mqttPublishMessage.payload());
        }
        return payload;
    }


    /**
     * 监测是否连接
     * @return bool
     */
    private boolean checkConnected() {
        if (deviceSessionCtx.isConnected()) {
            return true;
        } else {
            log.info("MqttTransportHandler.checkConnected [{}] [{}] Closing current session ", address,handlerId);
            return false;
        }
    }


    /**
     * 处理断开连接
     */
    private void doDisconnect() {
        if (deviceSessionCtx.isConnected()) {
            log.debug("MqttTransportHandler.doDisconnect >> [{}] Client disconnected!", handlerId);
            String hostName = address.getHostString();
            transportService.processDeviceDisConnect(deviceSessionCtx.getSessionId(), hostName);
            transportService.processLogDevice(deviceSessionCtx.getSessionId(), hostName);
            transportService.deregisterSession(deviceSessionCtx.getSessionId());
            deviceSessionCtx.setDisconnected();
        }
    }



    public static MqttPubAckMessage createMqttPubAckMsg(int msgId) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(PUBACK, false, AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader mqttMsgIdVariableHeader =
                MqttMessageIdVariableHeader.from(msgId);
        return new MqttPubAckMessage(mqttFixedHeader, mqttMsgIdVariableHeader);
    }

    private static MqttSubAckMessage createSubAckMessage(Integer msgId, List<Integer> grantedQosList) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(SUBACK, false, AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader = MqttMessageIdVariableHeader.from(msgId);
        MqttSubAckPayload mqttSubAckPayload = new MqttSubAckPayload(grantedQosList);
        return new MqttSubAckMessage(mqttFixedHeader, mqttMessageIdVariableHeader, mqttSubAckPayload);
    }

    private MqttMessage createUnSubAckMessage(int msgId) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(UNSUBACK, false, AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader = MqttMessageIdVariableHeader.from(msgId);
        return new MqttMessage(mqttFixedHeader, mqttMessageIdVariableHeader);
    }

    private <T> TransportServiceCallback<T> pubAckCallback(final ChannelHandlerContext ctx, final int msgId) {
        return new TransportServiceCallback<>() {
            @Override
            public void onSuccess(T dummy) {
                log.trace("MqttTransportHandler.pubAckCallback >> [{}][{}] message ack success,msgId:{}", handlerId,address,msgId );
                ack(ctx, msgId);
            }

            @Override
            public void onError(Throwable e) {
                log.trace("MqttTransportHandler.pubAckCallback >> [{}][{}] message ack failure,msgId:{}", handlerId,address,msgId );
                ctx.close();
            }
        };
    }


    private void ack(ChannelHandlerContext channelHandlerContext, int msgId) {
        if (msgId > 0) {
            channelHandlerContext.writeAndFlush(createMqttPubAckMsg(msgId));
        }
    }

    private  String getJsonPayload(ByteBuf payloadData)  {
        return payloadData.toString( StandardCharsets.UTF_8);
    }

    private  String getHexPayload( ByteBuf payloadData)  {
        return ByteBufUtil.hexDump(payloadData);
    }


    @Override
    public void on2DeviceConfigReq(String payload) {
        if (StringUtils.isBlank(payload)){
            return;
        }
        try {
            MqttPublishMessage publishMessage = getMqttPublishMessage(payload, CONFIG_REQ);
            deviceSessionCtx.getChannel().writeAndFlush(publishMessage);
        }catch (Exception e){
            log.info("MqttTransportHandler.on2DeviceConfigReq,is error:{},payload:{}",e.getMessage(),payload);
        }

    }

    @Override
    public void on2DeviceOtaReq(String payload) {
        if (StringUtils.isBlank(payload)){
            return;
        }
        try {
            MqttPublishMessage publishMessage = getMqttPublishMessage(payload, OTA_REQ);
            deviceSessionCtx.getChannel().writeAndFlush(publishMessage);
        }catch (Exception e){
            log.info("MqttTransportHandler.on2DeviceOtaReq,is error:{},payload:{}",e.getMessage(),payload);
        }

    }

    @Override
    public void on2DeviceControlReq(Integer sendId, String payload) {
        if (StringUtils.isBlank(payload)){
            return;
        }
        log.info("MqttTransportHandler.on2DeviceControlReq,sendId:{},payload:{}",sendId,payload);
        try {
            MqttPublishMessage publishMessage = getMqttPublishMessage(payload, CONTROL_REQ);
            int i = publishMessage.variableHeader().packetId();
            deviceSessionCtx.getChannel().writeAndFlush(publishMessage);
            transportService.processControlIsSend(sendId,i);
        }catch (Exception e){
            log.info("MqttTransportHandler.on2DeviceControlReq,is error:{},sendId:{},payload:{}",e.getMessage(),sendId,payload);
        }

    }



    @Override
    public void onCloseConnect() {
        deviceSessionCtx.getChannel().close();
    }

    private MqttPublishMessage getMqttPublishMessage(String payload, String topic) {
        byte [] bytes;
        if (DataFormat.JSON.equals(deviceSessionCtx.getDataFormat())){
            bytes = payload.getBytes(StandardCharsets.UTF_8);
        }else {
            bytes = HexTool.decodeHex(payload);
        }
        return createMqttPublishMsg(deviceSessionCtx, topic, bytes);
    }


    protected MqttPublishMessage createMqttPublishMsg( DeviceSessionCtx ctx, String topic, byte [] payloadByte) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(MqttMessageType.PUBLISH, false, AT_LEAST_ONCE, false, 0);
        MqttPublishVariableHeader header = new MqttPublishVariableHeader(topic, ctx.nextMsgId());
        ByteBuf payload = ALLOCATOR.buffer();
        payload.writeBytes(payloadByte);
        return new MqttPublishMessage(mqttFixedHeader, header, payload);
    }



}
