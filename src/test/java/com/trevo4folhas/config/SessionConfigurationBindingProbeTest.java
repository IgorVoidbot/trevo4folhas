package com.trevo4folhas.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;

/** Independent diagnostic probe: use real Boot binding, not textual YAML inspection. */
class SessionConfigurationBindingProbeTest {
  @Test
  void productionCookieSecurityMustBindToServerProperties() throws Exception {
    MutablePropertySources sources = new MutablePropertySources();
    for (var source :
        new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"))) {
      sources.addLast(source);
    }
    ServerProperties properties =
        new Binder(ConfigurationPropertySources.from(sources))
            .bind("server", Bindable.of(ServerProperties.class))
            .orElseGet(ServerProperties::new);
    var bean = new BeanWrapperImpl(properties);
    assertThat(bean.getPropertyValue("servlet.session.cookie.secure"))
        .as("production Secure flag must bind to server.servlet.session.cookie.secure")
        .isEqualTo(Boolean.TRUE);
    assertThat(bean.getPropertyValue("servlet.session.cookie.httpOnly")).isEqualTo(Boolean.TRUE);
    assertThat(String.valueOf(bean.getPropertyValue("servlet.session.cookie.sameSite")))
        .isEqualToIgnoringCase("LAX");
  }
}
