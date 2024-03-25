//package com.coco.boot.aspect;
//
//import jakarta.servlet.http.HttpServletResponse;
//import org.aspectj.lang.ProceedingJoinPoint;
//import org.aspectj.lang.annotation.Around;
//import org.aspectj.lang.annotation.Aspect;
//import org.aspectj.lang.annotation.Pointcut;
//import org.springframework.stereotype.Component;
//import org.springframework.web.context.request.RequestContextHolder;
//import org.springframework.web.context.request.ServletRequestAttributes;
//
//@Aspect
//@Component
//public class ResponseHeaderAspect {
//
//    @Pointcut("execution(public * *(..)) && @within(org.springframework.web.bind.annotation.RestController)")
//    public void controllerMethods() {
//    }
//
//    @Around("controllerMethods()")
//    public Object apiResponseAdvice(ProceedingJoinPoint pjp) throws Throwable {
//        Object proceed = pjp.proceed();
//        ServletRequestAttributes sra = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
//
//        if (sra != null) {
//            HttpServletResponse response = sra.getResponse();
//            if (response != null) {
//                response.setHeader("Content-Type", "application/json; charset=utf-8");
//            }
//        }
//
//        return proceed;
//    }
//}
