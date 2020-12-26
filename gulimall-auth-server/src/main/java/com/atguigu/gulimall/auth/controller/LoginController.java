package com.atguigu.gulimall.auth.controller;

import com.atguigu.gulimall.auth.vo.UserRegisterVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
public class LoginController {

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
        log.debug("Received reqeust: {}", vo.toString());
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
            redirectAttributes.addFlashAttribute("errors", errors);
            //校验出错，退回
            // Request method POST not supported
            // 用户注册 -》/register[post] -> 转发/reg.html（默认路径映射都是get方式访问，所以如果仍用forward转发会出错
            //return "forward:/reg.html";
            // 重新页面渲染
            return "redirect:http://auth.gulimall.com/reg.html";
        }
        //注册成功回到首页，回到登录页


        // login.html 前面的 / 代表直接回到本域名的资源
        return "redirect:/login.html";
    }
}
