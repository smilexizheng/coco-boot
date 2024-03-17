## coco-boot 测试springboot的项目

> 目前代码 情况，跳过了所有接口

* /coco/addTest添加 免登录的测试账号，默认创建10个系统可用的token，缓存到redis
* coco/v1/chat/completions  Authorization使用上面的token ，使用测试工具 验证并发 的限流效果

请根据实际情况 继续完善 api 接口的对接和响应。只是敲出来，未测试。
