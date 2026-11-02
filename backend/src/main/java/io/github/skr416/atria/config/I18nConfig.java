package io.github.skr416.atria.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Three-Tier Trilingual i18n Configuration (zh-TW, en-US, ja-JP) per ADR-0002.
 */
@Configuration
public class I18nConfig {

    public static final Locale LOCALE_ZH_TW = Locale.forLanguageTag("zh-TW");
    public static final Locale LOCALE_EN_US = Locale.forLanguageTag("en-US");
    public static final Locale LOCALE_JA_JP = Locale.forLanguageTag("ja-JP");

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(LOCALE_ZH_TW);
        resolver.setSupportedLocales(List.of(
                LOCALE_ZH_TW,
                LOCALE_EN_US,
                LOCALE_JA_JP,
                Locale.TRADITIONAL_CHINESE,
                Locale.US,
                Locale.ENGLISH,
                Locale.JAPAN,
                Locale.JAPANESE
        ));
        return resolver;
    }

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:i18n/messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setDefaultLocale(LOCALE_ZH_TW);
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }
}
