package com.accsaber.backend.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;

import com.accsaber.backend.model.entity.staff.StaffRole;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
public class OpenApiResponseConfig {

    private static final String ERROR_SCHEMA = "#/components/schemas/ErrorResponse";

    private static final Set<RequestMethod> WRITE_METHODS = Set.of(
            RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH);

    private static final Set<String> STAFF_ROLES = Stream.concat(
            Arrays.stream(StaffRole.values()).map(Enum::name), Stream.of("SERVICE")).collect(Collectors.toSet());

    @Bean
    public OperationCustomizer standardApiResponses() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            String authorization = authorizationExpression(handlerMethod);

            if (hasInput(operation)) {
                put(responses, "400", "Malformed parameter or body.");
            }
            if (authorization != null) {
                put(responses, "401", "Missing or expired token.");
                put(responses, "403", "Token lacks the required role.");
                applySecurity(operation, authorization);
            }
            if (hasPathParameter(operation)) {
                put(responses, "404", "Nothing exists at that id.");
            }
            if (isWrite(handlerMethod)) {
                put(responses, "409", "Conflicts with the current state.");
                put(responses, "422", "Validation failed. See fieldErrors.");
            }
            put(responses, "429", "Over 400 requests in 60 seconds.");
            put(responses, "500", "Server error. Quote the correlationId.");

            return operation;
        };
    }

    private void put(ApiResponses responses, String status, String description) {
        if (responses.containsKey(status)) {
            return;
        }
        responses.addApiResponse(status, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA)))));
    }

    private void applySecurity(Operation operation, String authorization) {
        if (operation.getSecurity() != null && !operation.getSecurity().isEmpty()) {
            return;
        }
        if (mentionsStaffRole(authorization)) {
            operation.setSecurity(List.of(
                    new SecurityRequirement().addList(OpenApiConfig.STAFF_TOKEN),
                    new SecurityRequirement().addList(OpenApiConfig.PLAYER_TOKEN)));
            return;
        }
        operation.setSecurity(List.of(new SecurityRequirement().addList(OpenApiConfig.PLAYER_TOKEN)));
    }

    private boolean mentionsStaffRole(String authorization) {
        return STAFF_ROLES.stream().anyMatch(role -> authorization.contains("'" + role + "'"));
    }

    private String authorizationExpression(HandlerMethod handlerMethod) {
        PreAuthorize onMethod = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(),
                PreAuthorize.class);
        if (onMethod != null) {
            return onMethod.value();
        }
        PreAuthorize onType = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(),
                PreAuthorize.class);
        return onType != null ? onType.value() : null;
    }

    private boolean isWrite(HandlerMethod handlerMethod) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(),
                RequestMapping.class);
        if (mapping == null) {
            return false;
        }
        for (RequestMethod verb : mapping.method()) {
            if (WRITE_METHODS.contains(verb)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInput(Operation operation) {
        return operation.getRequestBody() != null
                || (operation.getParameters() != null && !operation.getParameters().isEmpty());
    }

    private boolean hasPathParameter(Operation operation) {
        if (operation.getParameters() == null) {
            return false;
        }
        return operation.getParameters().stream().map(Parameter::getIn).anyMatch("path"::equals);
    }
}
