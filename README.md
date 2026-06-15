# Load Tester - Нагрузочный тестер REST API IfBest Enterprise

Консольная утилита на Java для нагрузочного тестирования REST API IfBest Enterprise. Создаёт параллельную нагрузку на API, измеряет время ответа и проверяет соответствие требованиям производительности (SLA).

## Требования

- Java 11 или выше
- Maven 3.6+
- Запущенный API IfBest Enterprise (http://localhost:8000)

## Сборка

```bash
mvn clean package
```
После сборки исполняемый jar файл появится в папке target/load-tester-1.0-SNAPSHOT.jar

## Запуск
```bash
java -jar target/load-tester-1.0-SNAPSHOT.jar [параметры]
```

## Параметры
| Параметр | Описание |
|---|---|
| --url | Базовый URL API |
| --scenario | Сценарий тестирования (search, login, videos) |
| --threads | Количество параллельных потоков |
| --requests | Общее количество запросов |
| --sla | Порог SLA в миллисекундах (для сценария search) |
| --think-time | Пауза между запросами в мс (имитация реальных пользователей) |

## Сценарии
- search - POST /api/search (поиск по транскриптам)
- login - POST /api/auth/login (авторизация)
- videos - GET /api/videos (список видео)

## Примеры запуска
```bash
#поиск
java -jar target/load-tester-1.0-SNAPSHOT.jar --url http://localhost:8000 --scenario search --threads 10 --requests 50

#авторизация
java -jar target/load-tester-1.0-SNAPSHOT.jar --url http://localhost:8000 --scenario login --threads 20 --requests 100

#видео
java -jar target/load-tester-1.0-SNAPSHOT.jar --url http://localhost:8000 --scenario videos --threads 15 --requests 200

#кастомный SLA
java -jar target/load-tester-1.0-SNAPSHOT.jar --url http://localhost:8000 --scenario search --threads 20 --requests 200 --sla 1500

#имитация пользователей
java -jar target/load-tester-1.0-SNAPSHOT.jar --url http://localhost:8000 --scenario search --threads 10 --requests 100 --think-time 300
```

## Пример отчёта
```bash
Нагрузочное тестирование
URL: http://localhost:8000
Сценарий: search
Потоков: 10
Всего запросов: 50
SLA порог: 2000.0 мс
Пользователей в пуле: 10

Создание пула тестовых пользователей...
Создано пользователей: 10

Отчёт о нагрузочном тестировании
Сценарий: search
Всего запросов: 50
Успешных: 50
Ошибок: 0
Общее время теста: 847 мс

Время ответа:
  Минимальное: 58 мс
  Максимальное: 363 мс
  Среднее: 163.66 мс

Перцентили:
  p50: 93.00 мс
  p90: 356.00 мс
  p95: 358.00 мс
  p99: 363.00 мс

RPS (запросов в секунду): 59.03

Проверка SLA (поиск < 2000.0 мс)
Запросов в SLA: 50 из 50 (100.0%)
SLA выполнен: p95 поиска = 358.00 мс (< 2000.0 мс)

Отчёт сохранён в файл: load-test-report-search.json
```

## Тестирование
```bash
mvn test
```
Тесты покрывают расчёт перцентилей, RPS и проверку SLA.
