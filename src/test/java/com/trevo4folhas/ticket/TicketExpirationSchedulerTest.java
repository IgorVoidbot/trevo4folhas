package com.trevo4folhas.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class TicketExpirationSchedulerTest {
  @Test
  void drainsMultipleBatchesWithABoundedStartupRun() {
    var service = mock(TicketExpirationService.class);
    when(service.expireBatch()).thenReturn(200);
    new TicketExpirationScheduler(service).expire();
    verify(service, times(20)).expireBatch();
    reset(service);
    when(service.expireBatch()).thenReturn(200, 200, 5);
    new TicketExpirationScheduler(service).expire();
    verify(service, times(3)).expireBatch();
  }

  @Test
  void storageFailureIsRedactedAndNextRunCanRecover() {
    var service = mock(TicketExpirationService.class);
    when(service.expireBatch())
        .thenThrow(new DataAccessResourceFailureException("Synthetic private detail"));
    Logger logger = (Logger) LoggerFactory.getLogger(TicketExpirationScheduler.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      var scheduler = new TicketExpirationScheduler(service);
      scheduler.expire();
      assertThat(appender.list).hasSize(1);
      assertThat(appender.list.get(0).getFormattedMessage())
          .contains("event=ticket_expiration_failed")
          .doesNotContain("Synthetic private detail");
      assertThat(appender.list.get(0).getThrowableProxy()).isNull();
      doReturn(0).when(service).expireBatch();
      scheduler.expire();
      verify(service, times(2)).expireBatch();
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }
}
