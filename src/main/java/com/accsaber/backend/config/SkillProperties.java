package com.accsaber.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "accsaber.skill")
@Getter
@Setter
public class SkillProperties {

    private double rankWeight = 0.70;
    private double combinedWeight = 0.30;
    private double rankCurveExponent = 0.5;
    private double sustainedCenter = 750;
    private double sustainedSpread = 90;
    private double peakCenter = 850;
    private double peakSpread = 90;
}
