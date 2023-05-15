package it.pagopa.interop.probing.response.updater.consumer;

import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.messaging.listener.SqsMessageDeletionPolicy;
import io.awspring.cloud.messaging.listener.annotation.SqsListener;
import it.pagopa.interop.probing.response.updater.dto.impl.ChangeResponseReceived;
import it.pagopa.interop.probing.response.updater.dto.impl.UpdateResponseReceivedDto;
import it.pagopa.interop.probing.response.updater.exception.EserviceNotFoundException;
import it.pagopa.interop.probing.response.updater.service.EserviceService;
import it.pagopa.interop.probing.response.updater.util.logging.Logger;
import it.pagopa.interop.probing.response.updater.util.logging.LoggingPlaceholders;


@Component
public class PollingReceiver {

  @Autowired
  private EserviceService eserviceService;

  @Autowired
  ObjectMapper mapper;

  @Autowired
  private Logger logger;

  @SqsListener(value = "${amazon.sqs.end-point.poll-result-queue}",
      deletionPolicy = SqsMessageDeletionPolicy.ON_SUCCESS)
  public void receiveStringMessage(final String message)
      throws IOException, EserviceNotFoundException {
    MDC.put(LoggingPlaceholders.TRACE_ID_PLACEHOLDER,
        "- [CID= " + UUID.randomUUID().toString().toLowerCase() + "]");

    logger.logConsumerMessage(message);

    try {
      UpdateResponseReceivedDto updateResponseReceived =
          mapper.readValue(message, UpdateResponseReceivedDto.class);

      logger.logMessageReceiver(updateResponseReceived.eserviceRecordId());

      eserviceService.updateResponseReceived(updateResponseReceived.eserviceRecordId(),
          ChangeResponseReceived.builder()
              .responseReceived(updateResponseReceived.responseReceived())
              .status(updateResponseReceived.status()).build());
    } finally {
      MDC.remove(LoggingPlaceholders.TRACE_ID_PLACEHOLDER);
    }
  }
}
