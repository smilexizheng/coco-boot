FROM maven:3.8-openjdk-17 AS build

RUN mkdir -p /app
WORKDIR /app

COPY . /app
RUN mvn clean package -Dmaven.test.skip=true

FROM openjdk:17-alpine
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.tuna.tsinghua.edu.cn/g' /etc/apk/repositories
RUN apk --no-cache update && apk --no-cache add curl ttf-dejavu fontconfig net-tools  busybox-extras && apk --no-cache upgrade
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo "Asia/Shanghai" > /etc/timezone
RUN mkdir /app/config -p
EXPOSE 8181
COPY --from=build /app/target/coco-boot.jar /app/

WORKDIR /app
CMD ["java", "-Dspring.config=config/application.yaml", "-jar", "/app/coco-boot.jar"]
