package com.ifbest;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LoadTester {
    private static final String[] SEARCH_QUERIES = {
        "тест", "видео", "презентация", "обучение", "документация",
        "инструкция", "отчёт", "анализ", "данные", "интервью",
        "конференция", "вебинар", "тренинг", "курс", "лекция"
    };

    private static final String[] MODES = {"keyword", "semantic", "hybrid"};
    private static final String TEST_PASSWORD = "TestPass123!";
    private static final int USER_POOL_SIZE = 10;

    private static class UserCredential {
        String email;
        String password;
        
        UserCredential(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        String tempBaseUrl = "http://localhost:8000";
        String tempScenario = "search";
        int tempThreads = 10;
        int tempTotalRequests = 50;
        double slaThreshold = 2000.0;
        int thinkTimeMs = 0;
        boolean enableThinkTime = false;

        for (int i = 0; i < args.length; i++) {
            if ("--url".equals(args[i]) && i + 1 < args.length) {
                tempBaseUrl = args[i + 1];
            } else if ("--scenario".equals(args[i]) && i + 1 < args.length) {
                tempScenario = args[i + 1];
            } else if ("--threads".equals(args[i]) && i + 1 < args.length) {
                tempThreads = Integer.parseInt(args[i + 1]);
            } else if ("--requests".equals(args[i]) && i + 1 < args.length) {
                tempTotalRequests = Integer.parseInt(args[i + 1]);
            } else if ("--sla".equals(args[i]) && i + 1 < args.length) {
                slaThreshold = Double.parseDouble(args[i + 1]);
            } else if ("--think-time".equals(args[i]) && i + 1 < args.length) {
                thinkTimeMs = Integer.parseInt(args[i + 1]);
                enableThinkTime = true;
            }
        }

        final String baseUrl = tempBaseUrl;
        final String scenario = tempScenario;
        final int threads = tempThreads;
        final int totalRequests = tempTotalRequests;
        final double finalSlaThreshold = slaThreshold;
        final int finalThinkTimeMs = thinkTimeMs;
        final boolean finalEnableThinkTime = enableThinkTime;

        OkHttpClient client = new OkHttpClient();
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");

        System.out.println("Нагрузочное тестирование");
        System.out.println("URL: " + baseUrl);
        System.out.println("Сценарий: " + scenario);
        System.out.println("Потоков: " + threads);
        System.out.println("Всего запросов: " + totalRequests);
        System.out.println("SLA порог: " + slaThreshold + " мс");
        System.out.println("Пользователей в пуле: " + USER_POOL_SIZE);
        if (enableThinkTime) {
            System.out.println("Think time: " + thinkTimeMs + " мс");
        }
        System.out.println();

        System.out.println("Создание пула тестовых пользователей...");
        List<UserCredential> userPool = createUserPool(client, baseUrl, JSON, USER_POOL_SIZE);
        if (userPool.isEmpty()) {
            System.out.println("Не удалось создать пользователей. Завершение.");
            return;
        }
        System.out.println("Создано пользователей: " + userPool.size());
        System.out.println();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        ConcurrentLinkedQueue<Long> responseTimes = new ConcurrentLinkedQueue<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        CountDownLatch latch = new CountDownLatch(totalRequests);
        long testStartTime = System.currentTimeMillis();
        Random random = new Random();

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    UserCredential randomUser = userPool.get(random.nextInt(userPool.size()));
                    
                    long startTime = System.currentTimeMillis();
                    
                    int statusCode = executeScenario(client, baseUrl, JSON, scenario, randomUser, random);
                    
                    long endTime = System.currentTimeMillis();
                    long duration = endTime - startTime;
                    
                    responseTimes.add(duration);
                    
                    if (statusCode == 200) {
                        successCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }

                    if (finalEnableThinkTime && finalThinkTimeMs > 0) {
                        Thread.sleep(finalThinkTimeMs + random.nextInt(finalThinkTimeMs));
                    }
                    
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long testEndTime = System.currentTimeMillis();
        long totalTestTime = testEndTime - testStartTime;
        
        executor.shutdown();

        List<Long> sortedTimes = responseTimes.stream()
                .sorted()
                .collect(Collectors.toList());

        long minTime = sortedTimes.isEmpty() ? 0 : sortedTimes.get(0);
        long maxTime = sortedTimes.isEmpty() ? 0 : sortedTimes.get(sortedTimes.size() - 1);
        double avgTime = sortedTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        
        double p50 = MetricsCalculator.calculatePercentile(sortedTimes, 50);
        double p90 = MetricsCalculator.calculatePercentile(sortedTimes, 90);
        double p95 = MetricsCalculator.calculatePercentile(sortedTimes, 95);
        double p99 = MetricsCalculator.calculatePercentile(sortedTimes, 99);

        double rps = MetricsCalculator.calculateRps(totalRequests, totalTestTime);

        long slaCompliantCount = sortedTimes.stream()
                .filter(time -> time <= finalSlaThreshold)
                .count();
        double slaCompliancePercent = (slaCompliantCount * 100.0) / totalRequests;
        boolean slaMet = MetricsCalculator.checkSla(p95, finalSlaThreshold);

        System.out.println();
        System.out.println("Отчёт о нагрузочном тестировании");
        System.out.println("Сценарий: " + scenario);
        System.out.println("Всего запросов: " + totalRequests);
        System.out.println("Успешных: " + successCount.get());
        System.out.println("Ошибок: " + errorCount.get());
        System.out.println("Общее время теста: " + totalTestTime + " мс");
        System.out.println();
        System.out.println("Время ответа:");
        System.out.println("  Минимальное: " + minTime + " мс");
        System.out.println("  Максимальное: " + maxTime + " мс");
        System.out.println("  Среднее: " + String.format("%.2f", avgTime) + " мс");
        System.out.println();
        System.out.println("Перцентили:");
        System.out.println("  p50: " + String.format("%.2f", p50) + " мс");
        System.out.println("  p90: " + String.format("%.2f", p90) + " мс");
        System.out.println("  p95: " + String.format("%.2f", p95) + " мс");
        System.out.println("  p99: " + String.format("%.2f", p99) + " мс");
        System.out.println();
        System.out.println("RPS (запросов в секунду): " + String.format("%.2f", rps));
        System.out.println();
        
        if ("search".equals(scenario)) {
            System.out.println("Проверка SLA (поиск < " + finalSlaThreshold + " мс)");
            System.out.println("Запросов в SLA: " + slaCompliantCount + " из " + totalRequests + " (" + String.format("%.1f", slaCompliancePercent) + "%)");
            
            if (slaMet) {
                System.out.println("SLA выполнен: p95 поиска = " + String.format("%.2f", p95) + " мс (< " + finalSlaThreshold + " мс)");
            } else {
                System.out.println("SLA нарушен: только " + String.format("%.1f", slaCompliancePercent) + "% запросов уложились в " + finalSlaThreshold + " мс");
            }
        }

        JsonObject report = new JsonObject();
        report.addProperty("scenario", scenario);
        report.addProperty("baseUrl", baseUrl);
        report.addProperty("threads", threads);
        report.addProperty("userPoolSize", userPool.size());
        report.addProperty("totalRequests", totalRequests);
        report.addProperty("successCount", successCount.get());
        report.addProperty("errorCount", errorCount.get());
        report.addProperty("totalTestTimeMs", totalTestTime);
        report.addProperty("minTimeMs", minTime);
        report.addProperty("maxTimeMs", maxTime);
        report.addProperty("avgTimeMs", avgTime);
        report.addProperty("p50Ms", p50);
        report.addProperty("p90Ms", p90);
        report.addProperty("p95Ms", p95);
        report.addProperty("p99Ms", p99);
        report.addProperty("rps", rps);
        
        if ("search".equals(scenario)) {
            report.addProperty("slaThresholdMs", finalSlaThreshold);
            report.addProperty("slaCompliancePercent", slaCompliancePercent);
            report.addProperty("slaMet", slaMet);
        }

        String fileName = "load-test-report-" + scenario + ".json";
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(fileName)) {
            gson.toJson(report, writer);
            System.out.println();
            System.out.println("Отчёт сохранён в файл: " + fileName);
        } catch (IOException e) {
            System.out.println("Ошибка при сохранении отчёта: " + e.getMessage());
        }
    }

    private static List<UserCredential> createUserPool(OkHttpClient client, String baseUrl, MediaType JSON, int poolSize) {
        List<UserCredential> users = new ArrayList<>();
        
        for (int i = 0; i < poolSize; i++) {
            String randomEmail = "loadtest_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
            
            boolean registered = registerUser(client, baseUrl, JSON, randomEmail, TEST_PASSWORD);
            
            if (registered) {
                users.add(new UserCredential(randomEmail, TEST_PASSWORD));
                System.out.println("  Создан: " + randomEmail);
            } else {
                System.out.println("  Не удалось создать: " + randomEmail);
            }
        }
        
        return users;
    }

    private static boolean registerUser(OkHttpClient client, String baseUrl, MediaType JSON, String email, String password) {
        try {
            String registerJson = String.format(
                "{\"email\":\"%s\",\"full_name\":\"Load Test User\",\"role\":\"director\",\"password\":\"%s\"}",
                email, password
            );
            
            RequestBody registerBody = RequestBody.create(JSON, registerJson);
            Request registerRequest = new Request.Builder()
                    .url(baseUrl + "/api/auth/register")
                    .post(registerBody)
                    .build();

            Response response = client.newCall(registerRequest).execute();
            int code = response.code();
            response.body().string();
            response.close();
            
            return code == 201 || code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static int executeScenario(OkHttpClient client, String baseUrl, MediaType JSON, String scenario, UserCredential user, Random random) throws IOException {
        switch (scenario) {
            case "login":
                return executeLoginScenario(client, baseUrl, JSON, user);
            case "videos":
                return executeVideosScenario(client, baseUrl, JSON, user, random);
            case "search":
                return executeSearchScenario(client, baseUrl, JSON, user, random);
            default:
                System.out.println("Неизвестный сценарий: " + scenario + ". Используем search.");
                return executeSearchScenario(client, baseUrl, JSON, user, random);
        }
    }

    private static int executeLoginScenario(OkHttpClient client, String baseUrl, MediaType JSON, UserCredential user) throws IOException {
        String loginJson = "{\"email\":\"" + user.email + "\",\"password\":\"" + user.password + "\"}";
        RequestBody loginRequestBody = RequestBody.create(JSON, loginJson);
        Request loginRequest = new Request.Builder()
                .url(baseUrl + "/api/auth/login")
                .post(loginRequestBody)
                .build();

        Response response = client.newCall(loginRequest).execute();
        int code = response.code();
        response.body().string();
        response.close();
        return code;
    }

    private static int executeVideosScenario(OkHttpClient client, String baseUrl, MediaType JSON, UserCredential user, Random random) throws IOException {
        String token = getToken(client, baseUrl, JSON, user.email, user.password);
        if (token == null) return 401;
        
        int page = 1 + random.nextInt(5);
        int pageSize = 10 + random.nextInt(40);
        
        Request request = new Request.Builder()
                .url(baseUrl + "/api/videos?page=" + page + "&page_size=" + pageSize)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        Response response = client.newCall(request).execute();
        int code = response.code();
        response.body().string();
        response.close();
        return code;
    }

    private static int executeSearchScenario(OkHttpClient client, String baseUrl, MediaType JSON, UserCredential user, Random random) throws IOException {
        String token = getToken(client, baseUrl, JSON, user.email, user.password);
        if (token == null) return 401;
        
        String query = SEARCH_QUERIES[random.nextInt(SEARCH_QUERIES.length)];
        String mode = MODES[random.nextInt(MODES.length)];
        int page = 1 + random.nextInt(3);
        int limit = 10 + random.nextInt(40);

        String searchJson = String.format(
            "{\"query\":\"%s\",\"mode\":\"%s\",\"page\":%d,\"limit\":%d}",
            query, mode, page, limit
        );

        RequestBody searchRequestBody = RequestBody.create(JSON, searchJson);
        Request searchRequest = new Request.Builder()
                .url(baseUrl + "/api/search")
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .post(searchRequestBody)
                .build();

        Response response = client.newCall(searchRequest).execute();
        int code = response.code();
        response.body().string();
        response.close();
        return code;
    }

    private static String getToken(OkHttpClient client, String baseUrl, MediaType JSON, String email, String password) {
        try {
            String loginJson = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
            RequestBody loginRequestBody = RequestBody.create(JSON, loginJson);
            Request loginRequest = new Request.Builder()
                    .url(baseUrl + "/api/auth/login")
                    .post(loginRequestBody)
                    .build();

            Response loginResponse = client.newCall(loginRequest).execute();
            String loginResponseBody = loginResponse.body().string();
            loginResponse.close();

            if (loginResponse.code() == 200) {
                JsonObject jsonResponse = JsonParser.parseString(loginResponseBody).getAsJsonObject();
                return jsonResponse.get("access_token").getAsString();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

}
