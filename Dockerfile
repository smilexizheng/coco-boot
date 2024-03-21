# 使用Maven官方镜像作为构建阶段的基础镜像
FROM maven:3.8-openjdk-17 AS build
RUN mkdir -p /app
# 将工作目录设置为/app
WORKDIR /app
# 复制pom.xml和源码到/app
COPY ./src /app/src
COPY ./pom.xml /app
# 使用Maven打包应用
RUN mvn clean package -Dmaven.test.skip=true

# 使用OpenJDK官方镜像作为运行阶段的基础镜像
FROM openjdk:17-alpine
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.tuna.tsinghua.edu.cn/g' /etc/apk/repositories
RUN apk --no-cache update && apk --no-cache add curl ttf-dejavu fontconfig net-tools  busybox-extras && apk --no-cache upgrade
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo "Asia/Shanghai" > /etc/timezone
RUN mkdir /app/conf -p
# 将工作目录设置为/app
WORKDIR /app
# 从构建阶段复制构建好的jar文件到/app
COPY --from=build /app/target/*.jar /app/app.jar
COPY --from=build /app/src/main/resources/application-docker.yml /app/conf/application.yml
# 定义环境变量，用于配置文件
# coco配置
ENV COCO_EXPIRATION_TTL=5 \
    COCO_REDIRECT_URI="" \
    COCO_CLIENT_ID="" \
    COCO_CLIENT_SECRET="" \
    COCO_AUTHORIZATION_ENDPOINT="" \
    COCO_TOKEN_ENDPOINT="" \
    COCO_USER_ENDPOINT="" \
    COCO_BASE_API="" \
    COCO_BASE_PROXY="" \
    COCO_FREQUENCY_TIME=1 \
    COCO_FREQUENCY_DEGREE=8 \
    COCO_USER_RATE_TIME=1 \
    COCO_USER_FREQUENCY_DEGREE=10 \
    COCO_USER_TOKEN_EXPIRE=1 \
    COCO_USER_LEVEL=2
# 风控参数
ENV RISK_CONTR_GET_TOKEN_NUM=10 \
    RISK_CONTR_TOKEN_MAX_REQ=500 \
    RISK_CONTR_USER_MAX_REQ=1000 \
    RISK_CONTR_USER_MAX_TIME=10 \
    RISK_CONTR_TOKEN_INVALID_NUM=10 \
    RISK_CONTR_REJECT_TIME_NUM=30 \
    RISK_CONTR_REJECT_TIME=2 \
    RISK_CONTR_BAN_NUM=5
# redisson配置
ENV REDISSON_MODE=single \
    REDISSON_DATABASE=0 \
    REDISSON_PASSWORD="" \
    REDISSON_SINGLE_ADDRESS="" \
    REDISSON_CLUSTER_MASTER_CONNECTION_POOL_SIZE=64 \
    REDISSON_CLUSTER_NODES="" \
    REDISSON_CLUSTER_RETRY_ATTEMPTS=3 \
    REDISSON_CLUSTER_RETRY_INTERVAL=1500 \
    REDISSON_CLUSTER_SCAN_INTERVAL=1000 \
    REDISSON_CLUSTER_SLAVE_CONNECTION_POOL_SIZE=64 \
    REDISSON_SENTINEL_NODES="" \
    REDISSON_SENTINEL_MASTER_NAME="" \
    REDISSON_SENTINEL_MASTER_ONLY_WRITE=true \
    REDISSON_TIMEOUT=3000 \
    REDISSON_READ_MODE=SLAVE \
    REDISSON_SUBSCRIPTION_MODE=SLAVE \
    REDISSON_POOL_CONN_TIMEOUT=3000 \
    REDISSON_POOL_MIN_IDLE=24 \
    REDISSON_POOL_SIZE=64 \
    REDISSON_POOL_SO_TIMEOUT=3000

# 暴露8181端口
EXPOSE 8181

# 运行Java应用
CMD ["java", "-Dspring.config=/app/conf/application.yml", "-jar", "/app/app.jar"]
