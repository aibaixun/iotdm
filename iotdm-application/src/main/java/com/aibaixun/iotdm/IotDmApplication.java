package com.aibaixun.iotdm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * IOTdm 应用程序启动类
 * @author wangxiao@aibaixun.com
 * @date 2022/3/3
 */
@EnableDiscoveryClient
@SpringBootApplication
public class IotDmApplication {

    public static void main(String[] args) {
        SpringApplication.run(IotDmApplication.class, args);
    }
}
