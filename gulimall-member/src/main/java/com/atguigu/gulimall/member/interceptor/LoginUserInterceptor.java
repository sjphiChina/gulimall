package com.atguigu.gulimall.member.interceptor;

import com.atguigu.common.constant.AuthServerConstant;
import com.atguigu.common.vo.MemberVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

//@Component 因为在GulimallWecConfig中生成了此类并添加到InterceptorRegistry，所以此行不在需要
@Slf4j
public class LoginUserInterceptor implements HandlerInterceptor {

    public static ThreadLocal<MemberVo> loginUser = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        log.info("Receive request: {}", request.getRequestURI());
        //tip 现在对于特定的请求匹配，匹配成功则放行，不用再检查登录
        String uri = request.getRequestURI();
        //一定要改成对login访问放行，否则memberVo为空，auth又无法产生memberVo,陷入死循环
        boolean match = new AntPathMatcher().match("/member/member/login/**", uri);
        if (match)
            return true;

        HttpSession session = request.getSession();
        MemberVo memberVo = (MemberVo) session.getAttribute(AuthServerConstant.LOGIN_USER);
        if (memberVo != null) {
            loginUser.set(memberVo);
        } else {
            log.warn("Redirect to: http://auth.gulimall.com/login.html");
            request.getSession().setAttribute("msg", "请先登录");
            response.sendRedirect("http://auth.gulimall.com/login.html");
            return false;
        }
        return true;
    }

//    @Override
//    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
//                           ModelAndView modelAndView) throws Exception {
//
//    }
}
