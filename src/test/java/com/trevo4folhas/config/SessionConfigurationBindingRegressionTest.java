package com.trevo4folhas.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;

/**
 * Permanent regression for the session/cookie binding contract (RNF-07) in both profiles: the
 * production posture in {@code application.yml} and the single local override in {@code
 * application-local.yml}. Uses real Boot binding — never textual YAML inspection — mirroring the
 * independent diagnostic probe that first proved the {@code server.servlet.session} defect.
 */
class SessionConfigurationBindingRegressionTest {

  @Test
  void baseProfileBindsTheProductionSessionPosture() throws Exception {
    BeanWrapperImpl bean = new BeanWrapperImpl(bind("application.yml", null));
    assertThat(bean.getPropertyValue("servlet.session.timeout")).isEqualTo(Duration.ofMinutes(30));
    assertThat(bean.getPropertyValue("servlet.session.cookie.httpOnly")).isEqualTo(Boolean.TRUE);
    assertThat(bean.getPropertyValue("servlet.session.cookie.secure")).isEqualTo(Boolean.TRUE);
    assertThat(String.valueOf(bean.getPropertyValue("servlet.session.cookie.sameSite")))
        .isEqualToIgnoringCase("LAX");
  }

  @Test
  void localProfileRelaxesOnlyTheSecureFlagKeepingTheRestOfTheContract() throws Exception {
    BeanWrapperImpl bean = new BeanWrapperImpl(bind("application.yml", "application-local.yml"));
    assertThat(bean.getPropertyValue("servlet.session.timeout")).isEqualTo(Duration.ofMinutes(30));
    assertThat(bean.getPropertyValue("servlet.session.cookie.httpOnly")).isEqualTo(Boolean.TRUE);
    assertThat(bean.getPropertyValue("servlet.session.cookie.secure")).isEqualTo(Boolean.FALSE);
    assertThat(String.valueOf(bean.getPropertyValue("servlet.session.cookie.sameSite")))
        .isEqualToIgnoringCase("LAX");
  }

  private static ServerProperties bind(String base, String overlay) throws Exception {
    MutablePropertySources sources = new MutablePropertySources();
    if (overlay != null) {
      // The profile-specific overlay takes precedence over the base document, as in the runtime.
      sources.addFirst(
          new YamlPropertySourceLoader()
              .load("application-local", new ClassPathResource(overlay))
              .get(0));
    }
    for (var source :
        new YamlPropertySourceLoader().load("application", new ClassPathResource(base))) {
      sources.addLast(source);
    }
    return new Binder(ConfigurationPropertySources.from(sources))
        .bind("server", Bindable.of(ServerProperties.class))
        .orElseGet(ServerProperties::new);
  }
}
