package com.coco.boot.controller;


import com.coco.boot.common.R;
import com.coco.boot.service.CoCoPilotService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;


/**
 * 处理类
 */
@AllArgsConstructor
@RestController
@RequestMapping("/coco")
public class CoCoPilotController {


    private final CoCoPilotService coCoPilotService;


    @GetMapping("/addTest")
    public R<String> sayHello() {
        return R.success(coCoPilotService.addTest());
    }


    /**
     * 上传ghu令牌
     *
     * @param data ghu
     */
    @PostMapping("upload")
    public R<String> uploadGhu(@RequestBody String data) {
        return coCoPilotService.updaloadGhu(data);
    }


    /**
     * 获取令牌
     *
     * @return
     */
    @GetMapping("/token")
    public ModelAndView token() {
        return coCoPilotService.token();
    }


    /**
     * 响应 L站 oauth2
     *
     * @param code
     * @param state
     * @return
     */
    @GetMapping("/oauth2/callback")
    public ResponseEntity<String> callback(@RequestParam String code, @RequestParam String state) {
        try {
            return coCoPilotService.callback(code, state);
        } catch (Exception e) {
            return new ResponseEntity<>("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @RequestMapping(value = "/v1/chat/completions", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> chat(@RequestBody Object requestBody, @RequestHeader("Authorization") String auth) {
        try {
            return coCoPilotService.chat(requestBody, auth);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


}
