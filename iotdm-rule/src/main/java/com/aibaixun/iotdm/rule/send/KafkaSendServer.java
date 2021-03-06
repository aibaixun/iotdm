package com.aibaixun.iotdm.rule.send;

import com.aibaixun.common.util.JsonUtil;
import com.aibaixun.iotdm.enums.ResourceType;
import com.aibaixun.iotdm.rule.pool.ResourceLruCache;
import com.aibaixun.iotdm.support.BaseResourceConfig;
import com.aibaixun.iotdm.support.BaseTargetConfig;
import com.aibaixun.iotdm.support.KafkaResourceConfig;
import com.aibaixun.iotdm.support.KafkaTargetConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Objects;
import java.util.Properties;

/**
 * @author huanghaijiang
 * kafka客户端服务
 */
@Component
public class KafkaSendServer implements SendServer {
    private final Logger log = LoggerFactory.getLogger(HttpSendServer.class);



    @Value("${bx.rule.kafka.max-idle-connections}")
    private Integer maxIdleConnections;

    private ResourceLruCache<KafkaProducer<String,String>> resourceLruCache;

    /**
     * 发送方法 需要子类实现
     *
     * @param message        消息
     * @param resourceConfig 资源配置
     * @param targetConfig   发送目标配置
     */
    @Override
    public <T> void doSendMessage(T message, BaseResourceConfig resourceConfig, BaseTargetConfig targetConfig) {
        KafkaResourceConfig kafkaResourceConfig = (KafkaResourceConfig) resourceConfig;
        try {
            KafkaProducer<String,String> kafkaProducer = generateClient(kafkaResourceConfig);
            KafkaTargetConfig kafkaTargetConfig = (KafkaTargetConfig) targetConfig;
            kafkaProducer.send(new ProducerRecord<>(kafkaTargetConfig.getTopic(), OBJECT_MAPPER.writeValueAsString(message)), (recordMetadata, e) -> {
                if (e != null) {
                    log.error("KafkaSendService.doSendMessage >> send result is error ,error msg is :{}", e.getMessage());
                } else {
                    log.info("KafkaSendService.doSendMessage >> is success , recordMetadata is :{}", recordMetadata);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            log.error("KafkaSendService.doSendMessage >> is error ,error msg is :{}", e.getMessage());
        }
    }

    /**
     * 需要子类实现 并调用registerService 方法
     */
    @Override
    @PostConstruct
    public void init() {
        registerService(ResourceType.KAFKA,this);
        resourceLruCache = new ResourceLruCache<>(maxIdleConnections);
    }


    public  KafkaProducer<String,String> generateClient(KafkaResourceConfig config) {
        String host = config.getHost();
        KafkaProducer<String, String> kafkaProducer = resourceLruCache.get(host);
        if (Objects.nonNull(kafkaProducer)){
            return kafkaProducer;
        }
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getHost());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        if (Objects.nonNull(config.getConnectTimeout()) && config.getConnectTimeout()!=0){
            properties.put(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, config.getConnectTimeout()*1000);
        }

        String compressionType = StringUtils.isNotEmpty(config.getCompressionType())?config.getCompressionType():"gzip";
        properties.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);
        if (Objects.nonNull(config.getReqTimeout()) && config.getReqTimeout() !=0){
            properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, config.getReqTimeout()*1000);
        }

        Integer metaUpdateTime  = Objects.nonNull(config.getMetadataUpdateTime())?config.getMetadataUpdateTime()*1000:60000;
        properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, metaUpdateTime);
        if (Objects.nonNull(config.getBufferSize())){
            properties.put(ProducerConfig.BUFFER_MEMORY_CONFIG, config.getBufferSize());
        }
        if (Objects.nonNull(config.getBatchSize())){
            properties.put(ProducerConfig.BATCH_SIZE_CONFIG, config.getBatchSize());
        }
        properties.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 1024*1024*10);

        if (Objects.nonNull(config.getUsername())){
            properties.put("username", config.getUsername());
        }
        if (Objects.nonNull(config.getPassword())){
            properties.put("password", config.getPassword());
        }
        kafkaProducer= new KafkaProducer<>(properties);
        resourceLruCache.put(host,kafkaProducer);
        return kafkaProducer;
    }


}
