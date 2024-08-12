package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.*;

public class CrptApi {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        this.semaphore = new Semaphore(requestLimit);
        this.scheduler = Executors.newScheduledThreadPool(1);

        scheduler.scheduleAtFixedRate(() ->
                semaphore.release(requestLimit - semaphore.availablePermits()),
                0, timeUnit.toMillis(1), TimeUnit.MILLISECONDS);
    }

    public static void main(String[] args) throws InterruptedException, IOException, ExecutionException {
        // Пример использования
        CrptApi api = new CrptApi(TimeUnit.MINUTES, 10);

        Product product = new Product("cert_doc", "2024-08-12", "cert_num", "owner_inn", "producer_inn",
                "2024-08-12", "tnved_code", "uit_code", "uitu_code");

        Document document = new Document("doc_id", "doc_status", "LP_INTRODUCE_GOODS", true,
                "owner_inn", "participant_inn", "producer_inn",
                "2024-08-12", "prod_type",
                new Product[]{product}, "2024-08-12", "reg_number");

        String response = api.createDocument(document, "signature");
        System.out.println("Response: " + response);

        api.shutdown();
    }

    public String createDocument(Document document, String signature) throws InterruptedException, IOException, ExecutionException {
        semaphore.acquire();

        String requestBody = objectMapper.writeValueAsString(document);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                .header("Content-Type", "application/json")
                .header("Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        CompletableFuture<HttpResponse<String>> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        return response.thenApply(HttpResponse::body).get();
    }

    /*
        По тексту задачи непонятно как должна передаваться Signature, я реализовал передачу в header, но возможна
        и перелача signature в теле запроса с помощью доп класса

        public static class DocumentWithSignature {
            private final Document document;
            private final String signature;

            public DocumentWithSignature(Document document, String signature) {
                this.document = document;
                this.signature = signature;
            }
        }
        */

    public void shutdown() {
        scheduler.shutdown();
    }

    public record Document(String doc_id, String doc_status, String doc_type, boolean importRequest, String owner_inn,
                           String participant_inn, String producer_inn, String production_date, String production_type,
                           Product[] products, String reg_date, String reg_number) {
    }

    public record Product(String certificate_document, String certificate_document_date,
                          String certificate_document_number, String owner_inn, String producer_inn,
                          String production_date, String tnved_code, String uit_code, String uitu_code) {
    }
}
