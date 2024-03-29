package it.pagopa.interop.probing.response.updater.consumer;

import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.TraceHeader;
import com.amazonaws.xray.spring.aop.XRayEnabled;
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
@XRayEnabled
public class PollingReceiver {

  @Autowired
  private EserviceService eserviceService;

  @Autowired
  private ObjectMapper mapper;

  @Autowired
  private Logger logger;

  @Value("${spring.application.name}")
  private String awsXraySegmentName;

  @SqsListener(value = "${amazon.sqs.end-point.update-response-received}",
      deletionPolicy = SqsMessageDeletionPolicy.ON_SUCCESS)
  public void receiveStringMessage(final Message message)
      throws IOException, EserviceNotFoundException {

    logger.logConsumerMessage(message.getBody());

    String traceHeaderStr = message.getAttributes().get("AWSTraceHeader");
    TraceHeader traceHeader = TraceHeader.fromString(traceHeaderStr);
    if (AWSXRay.getCurrentSegmentOptional().isEmpty()) {
      AWSXRay.getGlobalRecorder().beginSegment(awsXraySegmentName, traceHeader.getRootTraceId(),
          traceHeader.getParentId());
    }
    MDC.put(LoggingPlaceholders.TRACE_ID_XRAY_PLACEHOLDER,
        LoggingPlaceholders.TRACE_ID_XRAY_MDC_PREFIX
            + AWSXRay.getCurrentSegment().getTraceId().toString() + "]");
    try {
      UpdateResponseReceivedDto updateResponseReceived =
          mapper.readValue(message.getBody(), UpdateResponseReceivedDto.class);

      logger.logMessageReceiver(updateResponseReceived.eserviceRecordId());

      eserviceService.updateResponseReceived(updateResponseReceived.eserviceRecordId(),
          ChangeResponseReceived.builder()
              .responseReceived(updateResponseReceived.responseReceived())
              .status(updateResponseReceived.status()).build());
    } finally {
      MDC.remove(LoggingPlaceholders.TRACE_ID_XRAY_PLACEHOLDER);
    }
    AWSXRay.endSegment();
  }
}
