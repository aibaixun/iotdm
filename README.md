# IOTdm

IOTdm: 百讯物联网设备接入平台

## 设计思路与表结构设计

http://wiki.baixun.com/x/P4AWAQ 

## 功能
- 设备连接
- 数据解析
- 规则转发
- 在线调试
- 简单的数值计算 比如0XFFF 转int ,乘以100 保留小数点位数

## 包结构
```
iotdm
├── iotdm-application    应用程序启动，web api,设备数据业务处理逻辑
│   
├── iotdm-common         公共程序类,数据流转的实体类
│    
├── iotdm-rule           转发规则,消费消息队列 触发规则并转发出去
│    
└── iotdm-transport      设备接入与数据传输
dist 前端打包后文件
sql 第一次运行需要初始化的sql
```  
## 设备消息流程
Transport 接入设备,并开启socket,读取缓冲区数据,解析数据并发送Spring的Event</br>
Application 监听Event 并进行程序处理(json解析，物模型匹配),处理结束后转发到MQ</br>
Rule 监听MQ消息队列，并查询配置的规则,转发出去,每个转发是异步的(所以需要每次发送有超时时间)</br>

## 设备反向流程
Application 接受界面请求,并组装json(json里面包含类型与数据,类型主要是ota,control,config 具体含义后续补充);</br>
  Application 调用物模型插件函数,完成之后记录数据并生成唯一reqId并且发布Event </br>
Transport 监听上述Event 并且在内存中找到Socket信息的引用,见数据发布到缓冲区

## 数据发布
可以是数组,数据优先获取modelId的面的模型相关属性，不存在modelId则按照顺序匹配，匹配上结束
```json
{
  "key": "value",
  "modelId": "模型名称" // 可以有 可以没,存在的话名称必须在模型中定义
}
```
二进制报文
```
 0101010101
```

## 说明 
- 未使用百讯模版中db与redis，需要一些定制化开发
- redis 开启订阅通知
1. 修改redis.conf 配置文件
```
# 原来是""空
notify-keyspace-events "Ex"
```
**如果是运行中redis 使用如下命令更改,集群每个节点都要执行该命令**
```
config set notify-keyspace-events Ex
```
2. decode 需要js 插件解析
```js
var jsonObj = {};
var uint8Array = new Uint8Array(payload.length);
for (var i = 0; i < payload.length; i++) {
	uint8Array[i] = payload[i] & 0xff;
}
var dataView = new DataView(uint8Array.buffer, 0);
var tem = dataView.getInt8(0);
jsonObj = {
	"tem": tem
};
return JSON.stringify(jsonObj);
```
3. encode js
```js
{
	"toDeviceType": "COMMAND", // 枚举类 分别是 COMMAND，CONFIG，OTA
	"commandLabel": "", //命令名称
	"modelId": "" //模型id  允许为空
	"params": {
		"key1": "value1",
		"key2": 10
	}
}
```

## 分支说明 
v1.0.0 分支是基础版本开发分支，该分支主要迭代的功能是设备mqtt 连接，基础消息转发</br>
待完成的功能点如下：
- [ ] TCP 连接服务
- [ ] 设备联动
- [ ] 数据持久化配置

## 快速开始
- 执行init.sql
- 启动项目
- 将前端放置在nginx 服务

## 演示
- 首页
  [![OncQxS.png](https://s1.ax1x.com/2022/05/06/OncQxS.png)](https://imgtu.com/i/OncQxS)
- 产品列表
  [![OnRqiR.png](https://s1.ax1x.com/2022/05/06/OnRqiR.png)](https://imgtu.com/i/OnRqiR)
- 产品模型
  [![OnRvQK.png](https://s1.ax1x.com/2022/05/06/OnRvQK.png)](https://imgtu.com/i/OnRvQK)
- 脚本解析
  [![OnflHe.png](https://s1.ax1x.com/2022/05/06/OnflHe.png)](https://imgtu.com/i/OnflHe)
- 设备管理
  [![OnWy6K.png](https://s1.ax1x.com/2022/05/06/OnWy6K.png)](https://imgtu.com/i/OnWy6K)
  [![OnWvhn.png](https://s1.ax1x.com/2022/05/06/OnWvhn.png)](https://imgtu.com/i/OnWvhn)
- 规则转发
  [![Onfgg0.png](https://s1.ax1x.com/2022/05/06/Onfgg0.png)](https://imgtu.com/i/Onfgg0)
  [![Onf2vV.png](https://s1.ax1x.com/2022/05/06/Onf2vV.png)](https://imgtu.com/i/Onf2vV)
- 资源管理
  [![OnffDU.png](https://s1.ax1x.com/2022/05/06/OnffDU.png)](https://imgtu.com/i/OnffDU)
- 新手指引
  [![On2Pne.png](https://s1.ax1x.com/2022/05/06/On2Pne.png)](https://imgtu.com/i/On2Pne)
- 在线调试
  [![Onfo59.png](https://s1.ax1x.com/2022/05/06/Onfo59.png)](https://imgtu.com/i/Onfo59)
## 联系人 
wangxiao@aibaixun.com

## 所有权
贵州百讯智汇科技有限公司
