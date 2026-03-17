package com.accsaber.backend.config;

import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

public class NullsLastPageableResolver extends PageableHandlerMethodArgumentResolver {

    @Override
    public Pageable resolveArgument(MethodParameter methodParameter,
                                    ModelAndViewContainer mavContainer,
                                    NativeWebRequest webRequest,
                                    WebDataBinderFactory binderFactory) {
        Pageable pageable = super.resolveArgument(methodParameter, mavContainer, webRequest, binderFactory);

        if (pageable.isUnpaged() || pageable.getSort().isUnsorted()) {
            return pageable;
        }

        Sort nullsLastSort = Sort.by(
                pageable.getSort().stream()
                        .map(order -> order.with(Sort.NullHandling.NULLS_LAST))
                        .toList()
        );

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), nullsLastSort);
    }
}
