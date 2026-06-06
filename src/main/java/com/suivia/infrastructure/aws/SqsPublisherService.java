package com.suivia.infrastructure.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suivia.infrastructure.config.AwsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class SqsPublisherService {

    private final SqsClient sqsClient;
    private final AwsProperties awsProperties;
    private final ObjectMapper objectMapper;

    /**
     * Publica uma mensagem JSON na fila SQS para o Worker processar.
     */
    public void publishInvoiceEvent(Object eventPayload) {
        try {
            String messageBody = objectMapper.writeValueAsString(eventPayload);

            // Busca a URL real da fila pelo nome
            String queueUrl = sqsClient.getQueueUrl(r -> r.queueName(awsProperties.getSqsQueue()))
                    .queueUrl();

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .build();

            sqsClient.sendMessage(request);
            log.info("Evento publicado na fila SQS [{}]: {}", awsProperties.getSqsQueue(), messageBody);

        } catch (Exception e) {
            log.error("Erro ao publicar evento no SQS: {}", e.getMessage());
            throw new RuntimeException("Falha ao publicar evento no SQS", e);
        }
    }
}
