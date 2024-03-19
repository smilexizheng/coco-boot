package com.coco.boot.interceptor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.coco.boot.config.CoCoConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;

import static com.coco.boot.constant.SysConstant.GHU_ALIVE_KEY;
import static com.coco.boot.constant.SysConstant.SYS_USER_ID;
import static com.coco.boot.constant.SysConstant.USING_USER;

/**
 * @author Fengdong.Duan
 * @create 2024/3/18 9:41
 */
public class ChatInterceptor implements HandlerInterceptor {
    public static ThreadLocal<JSONObject> tl = new ThreadLocal<>();
    private final RedissonClient redissonClient;
    private final CoCoConfig coCoConfig;

    public ChatInterceptor() {
        this.redissonClient= SpringUtil.getBean(RedissonClient.class);
        this.coCoConfig= SpringUtil.getBean(CoCoConfig.class);
    }


    /**
     * 拦截请求，检查请求头中的Authorization字段是否为有效的token，并判断token是否可用
     *
     * @param request HTTP请求
     * @param response HTTP响应
     * @param handler 处理器
     * @return 是否继续执行处理器
     * @throws Exception 异常信息
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String auth = request.getHeader("Authorization");
        if (StrUtil.isNotBlank(auth) && StrUtil.startWith(auth, "Bearer ")) {
            String token = auth.substring("Bearer ".length());
            RBucket<String> bucket = redissonClient.getBucket(SYS_USER_ID + token);
            if (!bucket.isExists()) {
                setResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token does not exist");
                return false;
            }
            // 再判断是否有可用的key
            RSet<String> ghuAliveKey = redissonClient.getSet(GHU_ALIVE_KEY, StringCodec.INSTANCE);
            if (!ghuAliveKey.isExists()) {
                setResponse(response, HttpServletResponse.SC_OK, "{\"message\": \"No keys\"}");
                return false;
            }
            bucket.expireAsync(Duration.ofHours(coCoConfig.getUserTokenExpire()));
            JSONObject userInfo = JSON.parseObject(bucket.get());
            tl.set(userInfo);
            return true;
        }
        setResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid Authorization");
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 由于handleProxy中的判断为is2xxSuccessful(),对齐状态码
        HttpStatus status = HttpStatus.resolve(response.getStatus());
        if (status != null && status.is2xxSuccessful()) {
            // 用户访问计数
            RAtomicLong atomicLong = this.redissonClient.getAtomicLong(USING_USER + tl.get().getString("id"));
            atomicLong.incrementAndGet();
        }
        tl.remove();
    }

    /**
     * 设置HTTP响应的状态码和消息体
     *
     * @param response HTTP响应
     * @param status 状态码
     * @param msg 消息体
     * @throws IOException IO异常
     */
    private void setResponse(HttpServletResponse response, int status, String msg) throws IOException {
        response.setStatus(status);
        response.getWriter().write(msg);
    }
}
