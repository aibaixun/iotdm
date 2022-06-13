package com.aibaixun.iotdm.util;

import com.aibaixun.basic.util.UserSessionUtil;

/**
 * @author wangxiao@aibaixun.com
 * @date 2022/3/3
 */
public class UserInfoUtil {


    private UserInfoUtil(){}


    public static String getTenantIdOfNull() {
        return UserSessionUtil.getCurrentSessionTid();
    }

    public static String getUserIdOfNull(){
        return UserSessionUtil.getCurrentSessionUid();
    }

}
