package com.atguigu.gulimall.member.vo;

import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;

@Data
public class MemberRegisterVo {
    private String userName;
    private String password;
    private String phone;
    //@NotEmpty(message = "用户名必须提交")
    private String code;
}
