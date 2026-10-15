package com.accsaber.backend.config;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.servlet.support.ExtendedServletRequestDataBinder;

@ControllerAdvice
public class RequestBindingAdvice {

    @InitBinder
    public void ignoreHeaders(WebDataBinder binder) {
        if (binder instanceof ExtendedServletRequestDataBinder servletBinder) {
            servletBinder.setHeaderPredicate(header -> false);
        }
    }
}
