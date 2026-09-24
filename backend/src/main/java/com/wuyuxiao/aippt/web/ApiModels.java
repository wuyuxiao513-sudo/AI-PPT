package com.wuyuxiao.aippt.web;
import jakarta.validation.constraints.*;
import java.util.List;
public final class ApiModels {
    private ApiModels() {}
    public record OutlineUpdate(@NotBlank String title, @NotEmpty List<OutlineItem> slides, String theme) {}
    public record OutlineItem(@NotBlank String title, String subtitle, String layout) {}
}
