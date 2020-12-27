package com.atguigu.gulimall.member.vo;

import com.atguigu.common.vo.MemberVo;
import com.atguigu.gulimall.member.entity.MemberEntity;

public class MemberEntityToVo {
    public static MemberVo converToVo(MemberEntity entity) {
        MemberVo vo = new MemberVo();
        vo.setUsername(entity.getUsername());
        vo.setEmail(entity.getEmail());
        vo.setNickname(entity.getNickname());
        vo.setMobile(entity.getMobile());
        return vo;
    }
}
