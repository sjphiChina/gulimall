package com.atguigu.gulimall.cart.interceptor;

import com.atguigu.common.constant.AuthServerConstant;
import com.atguigu.common.constant.CartConstant;
import com.atguigu.common.vo.MemberVo;
import com.atguigu.gulimall.cart.vo.UserInfoTo;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.UUID;

//@Component 因为在GulimallWecConfig中生成了此类并添加到InterceptorRegistry，所以此行不在需要
public class CartInterceptor implements HandlerInterceptor {

    public static ThreadLocal<UserInfoTo> threadLocal = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        UserInfoTo userInfoTo = new UserInfoTo();
        HttpSession session = request.getSession();
        MemberVo memberVo = (MemberVo) session.getAttribute(AuthServerConstant.LOGIN_USER);
        if (memberVo != null) {

            userInfoTo.setUserId(memberVo.getId());
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null && cookies.length > 0) {
            for (Cookie cookie: cookies) {
                String name = cookie.getName();
                if (name.equals(CartConstant.TEMP_USER_COOKIE_NAME)) {
                    userInfoTo.setUserKey(cookie.getValue());
                    userInfoTo.setTempUser(true);

                }
            }
        }
        if (StringUtils.isEmpty(userInfoTo.getUserKey())) {
            String uuid = UUID.randomUUID().toString();
            userInfoTo.setUserKey(uuid);
        }

        threadLocal.set(userInfoTo);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) throws Exception {
        UserInfoTo userInfoTo = threadLocal.get();
        //如果没有临时用户，一定保存一个临时用户
        if (!userInfoTo.isTempUser()) {
            Cookie cookie = new Cookie(CartConstant.TEMP_USER_COOKIE_NAME, userInfoTo.getUserKey());
            cookie.setDomain("gulimall.com");
            cookie.setMaxAge(CartConstant.TEMP_USER_COOKIE_TIMEOUT);
            response.addCookie(cookie);
        }
    }
}
