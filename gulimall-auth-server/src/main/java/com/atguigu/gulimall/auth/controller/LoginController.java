package com.atguigu.gulimall.auth.controller;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.auth.feign.MemberFeignService;
import com.atguigu.gulimall.auth.vo.UserLoginVo;
import com.atguigu.gulimall.auth.vo.UserRegisterVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
import java.lang.reflect.Member;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
public class LoginController {

    @Autowired
    MemberFeignService memberFeignService;

      // GulimallWebConfig 中配置了addViewControllers，一下两个简单方法可以省略
//    @GetMapping("/login.html")
//    public String loginPage() {
//        return "login";
//    }
//
//    @GetMapping("/reg.html")
//    public String regPage() {
//        return "reg";
//    }

    // RedirectAttributes redirectAttributes 模拟重定向携带数据
    @PostMapping("/register")
    public String register(@Valid UserRegisterVo vo, BindingResult result, RedirectAttributes redirectAttributes) {
        log.debug("Received register request: {}", vo.toString());
        if (result.hasErrors()) {
//            result.getFieldErrors().stream().map(fieldError -> {
//                String field = fieldError.getField();
//                String message = fieldError.getDefaultMessage();
//                erros.put(field, message);
//                return Void;
//            });

//            Map<String, String> errors = result.getFieldErrors().stream().collect(Collectors.toMap(fieldError -> {
//                return fieldError.getField();
//            },fieldError -> {
//                return fieldError.getDefaultMessage();
//            }));

            Map<String, String> errors = result.getFieldErrors().stream()
                    .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage));
            //model.addAttribute("errors", errors);
            //addFlashAttribute()  数据只能被保存访问一次
            log.error("error: {}", errors.values().toString());
            redirectAttributes.addFlashAttribute("errors", errors);
            //校验出错，退回
            // Request method POST not supported
            // 用户注册 -》/register[post] -> 转发/reg.html（默认路径映射都是get方式访问，所以如果仍用forward转发会出错
            //return "forward:/reg.html";
            // 重新页面渲染
            return "redirect:http://auth.gulimall.com/reg.html";
        }
        //注册成功回到首页，回到登录页

        R r = memberFeignService.register(vo);
        if (r.getCode() == 0) {
            //成功
            return "redirect:http://auth.gulimall.com/login.html";
        } else {
            Map<String, String> errors = new HashMap<>();
            errors.put("msg",r.getMsg());
            redirectAttributes.addFlashAttribute("errors", errors);
            log.error("error: {}", r.getData(new TypeReference<String>(){}));
            return "redirect:http://auth.gulimall.com/reg.html";
        }

        // login.html 前面的 / 代表直接回到本域名的资源
        //return "redirect:/login.html";
    }

    @PostMapping("/login")
    public String register(UserLoginVo vo, RedirectAttributes redirectAttributes) {

        log.debug("Received login request: {}", vo.toString());
        R login = memberFeignService.login(vo);
        if (login.getCode() == 0) {
            return "redirect:http://gulimall.com";
        }
        Map<String, String> errors = new HashMap<>();
        //errors.put("msg",login.getData("msg", new TypeReference<String>(){}));
        errors.put("msg",login.getMsg());
        redirectAttributes.addFlashAttribute("errors", errors);
        return "redirect:http://auth.gulimall.com/login.html";
    }
}
