package com.coco.boot.controller;

import com.coco.boot.common.R;
import com.coco.boot.entity.ServiceStatus;
import com.coco.boot.pojo.Conversation;
import com.coco.boot.service.CoCoPilotService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

/**
 * 处理类
 */
@AllArgsConstructor
@RestController
@RequestMapping("/")
@Slf4j
public class CoCoPilotController {


    private final CoCoPilotService coCoPilotService;

    /**
     * 上传ghu令牌
     *
     * @param data ghu
     */
    @PostMapping("upload")
    public R<String> uploadGhu(@RequestBody String data) {
        return coCoPilotService.uploadGhu(data);
    }


    /**
     * 获取令牌
     */
    @GetMapping("/token")
    public ModelAndView token() {
        return coCoPilotService.token();
    }


    /**
     * 响应 L站 oauth2
     */
    @GetMapping("/callback")
    public ResponseEntity<String> callback(@RequestParam String code, @RequestParam String state) {
        try {
            return coCoPilotService.callback(code, state);
        } catch (Exception e) {
            return new ResponseEntity<>("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @RequestMapping(value = "/v1/**", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> chat(@RequestBody Conversation requestBody,
                               @RequestHeader("Authorization") String auth,
                               HttpServletRequest request,
                               HttpServletResponse response) {
        try {
            log.info("request = {}", request.getRequestURI());
            return coCoPilotService.chat(requestBody, auth, request.getRequestURI(), response);
        } catch (Exception e) {
            log.error("chat error", e);
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    /**
     * 服务状态查询
     */
    @GetMapping("/service-status")
    public R<ServiceStatus> serviceStatus() {
        return R.success(coCoPilotService.getServiceStatus());
    }


}
