# 使用Maven官方镜像作为构建阶段的基础镜像
FROM maven:3.8.4-openjdk-17-slim AS build
# 将工作目录设置为/app
WORKDIR /app
# 复制pom.xml和源码到/app
COPY pom.xml .
COPY src ./src
# 使用Maven打包应用
RUN mvn clean package -DskipTests

# 使用OpenJDK官方镜像作为运行阶段的基础镜像
FROM openjdk:17-slim
# 将工作目录设置为/app
WORKDIR /app
# 从构建阶段复制构建好的jar文件到/app
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/target/redisson-config.yml ./conf/redisson-config.jar
# 定义环境变量，用于配置文件
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
    COCO_USER_RATE_TIME=5 \
    COCO_USER_FREQUENCY_DEGREE=1 \
    COCO_USER_TOKEN_EXPIRE=1 \
    SPRING_PROFILES_ACTIVE=docker

# 暴露8181端口
EXPOSE 8181

# 运行Java应用
CMD ["java", "-jar", "app.jar"]
