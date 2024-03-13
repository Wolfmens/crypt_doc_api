package org.test;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
@NoArgsConstructor
public class CrptApi {

    private TimeUnit timeRangeForRequests;

    private Integer countRequestsInTimeDuration;

    private Long rateLimit;

    private OkHttpClient client = new OkHttpClient();

    private ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, List<Long>> requestCountsByAddressCache = new ConcurrentHashMap<>();

    public CrptApi(Long rateLimit, TimeUnit timeRangeForRequests, Integer countRequestsInTimeDuration) {
        this.timeRangeForRequests = timeRangeForRequests;
        this.countRequestsInTimeDuration = countRequestsInTimeDuration;
        this.rateLimit = rateLimit;
        client.interceptors().add(
                new RateLimitInterceptor(
                        rateLimit,
                        timeRangeForRequests,
                        countRequestsInTimeDuration));
    }

    @SneakyThrows
    public synchronized Object createDocumentForSendGoodForSale(UpsertClientRequest requestData, String signature) {
        MediaType json = MediaType.get("application/json;charset=utf-8");

        String requestBody = objectMapper.writeValueAsString(requestData);
        RequestBody body = RequestBody.create(requestBody, json);

        Request httpRequest = new Request
                .Builder()
                .url("https://ismp.crpt.ru/api/v3/lk/documents/create")
                .addHeader("Authorization", signature)
                .post(body)
                .build();

        try (Response response = client.newCall(httpRequest).execute()) {
            ResponseBody responseBody = response.body();

            if (responseBody != null) {
                String bodyResp = responseBody.string();

                return objectMapper.readValue(bodyResp, Object.class);
            } else {
                throw new RuntimeException("Response body is empty");
            }
        }
    }

    @Setter
    @Getter
    public class UpsertClientRequest {

        private Description description = new Description();
        private String doc_id = "string";
        private String doc_status = "string";
        private String doc_type = "LP_INTRODUCE_GOODS";
        private Boolean importRequest = true;
        private String owner_inn = "string";
        private String participant_inn = "string";
        private String producer_inn = "string";
        private String production_date = "string";
        private String production_type = "string";
        private List<Map<String, String>> products =
                List.of(Map.of(
                        "certificate_document", "string",
                        "certificate_document_date", "2020-01-23",
                        "certificate_document_number", "string",
                        "owner_inn", "string",
                        "producer_inn", "string",
                        "production_date", "2020-01-23",
                        "tnved_code", "string",
                        "uit_code", "string",
                        "uitu_code", "string")
                );
        private String reg_date = "2020-01-23";
        private String reg_number = "string";

        @Getter
        @Setter
        public class Description {

            private String participantInn = "string";

        }
    }

    private class RateLimitInterceptor implements Interceptor {

        private TimeUnit timeRangeForRequests;

        private Integer countRequestsInTimeDuration;

        private Long timeRateLimit;


        public RateLimitInterceptor(Long rateLimit, TimeUnit timeRangeForRequests, Integer countRequestsInTimeDuration) {
            this.timeRangeForRequests = timeRangeForRequests;
            this.countRequestsInTimeDuration = countRequestsInTimeDuration;
            timeRateLimit = rateLimit;
        }

        @NotNull
        @Override
        @SneakyThrows
        public Response intercept(@NotNull Chain chain) throws IOException {
            String address = chain.connection().socket().getInetAddress().getHostAddress();
            Long currentTime = System.currentTimeMillis();
            requestCountsByAddressCache.putIfAbsent(address, new ArrayList<>());
            requestCountsByAddressCache.get(address).add(currentTime);
            cleanUpRequestCounts(currentTime);

            if (requestCountsByAddressCache.get(address).size() > countRequestsInTimeDuration) {
                Thread.sleep(20000);
                return intercept(chain);
            }

            return chain.proceed(chain.request());
        }

        private void cleanUpRequestCounts(Long currentTime) {
            requestCountsByAddressCache.values().forEach(v -> {
                v.removeIf(timeForCheck -> timeIsTooOld(currentTime, timeForCheck));
            });

        }

        private boolean timeIsTooOld(Long currentTime, Long timeForCheck) {
            return currentTime - timeForCheck > TimeUnit.MILLISECONDS.convert(timeRateLimit, timeRangeForRequests);
        }
    }

}

