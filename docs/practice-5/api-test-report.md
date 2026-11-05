# API-тесты СМП - отчёт

Модуль `services/api-tests`: Java 21, Maven, JUnit 5, REST-assured 5.4.0, Hamcrest, Awaitility 4.2.2, JSON Schema Validator.
Всего 302 запуска тестов: 100 методов в 37 классах, значения классов эквивалентности собраны в таблицы параметров. Полный прогон занимает около 5,5 минут.

Расхождения обозначены D-01..D-39 (раздел 4). Тест с таким расхождением помечен `@KnownDiscrepancy` и фиксирует наблюдаемое поведение: как только расхождение устранят, он покраснеет, и запись можно будет закрыть. Расхождения низкой значимости, D-21, D-35, D-37 и D-39 зафиксированы без отдельного теста.

## 1. Покрытие

Таблица собрана из журнала запросов `target/api-coverage.log` (его пишет `CoverageFilter`) и отчётов surefire. Статус-коды в ней наблюдались фактически. «Кол-во проверок» - число запусков тестов, для которых этот запрос является проверяемым действием, а не подготовкой данных. Запросы подготовки (например, `POST /api/cards` в начале теста) дают статусы в таблице, но не считаются проверками.

| Эндпоинт | Проверяемые статус-коды | Кол-во проверок | Статус |
|---|---|---|---|
| `GET /api/bin/{bin}` (Bin Lookup) | 200, 404, 500 | 3 | покрыт |
| `GET /api/cards` (Gateway) | 503 | 1 | покрыт |
| `GET /api/cards` (CMS) | 200, 400, 413, 500 | 21 | покрыт |
| `POST /api/cards` (Gateway) | 201, 400, 404, 503 | 20 | покрыт |
| `POST /api/cards/bulk-update` (Gateway) | 200, 400, 503 | 5 | покрыт |
| `POST /api/cards/generate` (Gateway) | 201, 400, 404, 503 | 6 | покрыт |
| `DELETE /api/cards/{pan}` (Gateway) | 204, 400, 404, 503 | 4 | покрыт |
| `GET /api/cards/{pan}` (Gateway) | 200, 400, 404, 503 | 10 | покрыт |
| `GET /api/cards/{pan}` (CMS) | 200, 404 | 1 | покрыт |
| `PATCH /api/cards/{pan}` (Gateway) | 200, 503 | 6 | покрыт |
| `PATCH /api/cards/{pan}` (CMS) | 400, 404, 500 | 5 | покрыт |
| `POST /api/cards/{pan}/reserve` (Gateway) | 200, 400, 402, 404, 409, 503 | 15 | покрыт |
| `POST /api/cards/{pan}/rollback` (Gateway) | 200, 400, 404, 409, 503 | 8 | покрыт |
| `GET /api/dashboard/charts` (Gateway) | 200, 400, 503 | 6 | покрыт |
| `GET /api/dashboard/recent` (Gateway) | 200, 400, 503 | 7 | покрыт |
| `GET /api/dashboard/stats` (Gateway) | 200, 503 | 2 | покрыт |
| `POST /api/internal/authorize` (Gateway) | 404 | 1 | покрыт |
| `POST /api/internal/authorize` (Authorization) | 200, 400, 403, 404, 422, 503 | 11 | покрыт |
| `POST /api/internal/log` (Gateway) | 404 | 1 | покрыт |
| `POST /api/internal/log` (Logger) | 200, 201, 400, 409 | 12 | покрыт |
| `POST /api/internal/rollback` (Authorization) | 200, 400, 404, 409 | 4 | покрыт |
| `POST /api/internal/route` (Gateway) | 404 | 1 | покрыт |
| `POST /api/internal/route` (Switch) | 200, 400 | 2 | покрыт |
| `POST /api/simulator/merchant/fee` (Gateway) | 200, 400, 404, 503 | 4 | покрыт |
| `POST /api/simulator/merchant/run` (Gateway) | 200, 400, 404, 503 | 9 | покрыт |
| `POST /api/simulator/merchant/run` (Merchant) | 502 | 1 | покрыт |
| `GET /api/simulator/merchants` (Gateway) | 404 | 1 | покрыт |
| `POST /api/simulator/terminal/run` (Gateway) | 200, 400, 503 | 9 | покрыт |
| `POST /api/simulator/terminal/run` (Terminal) | 503 | 1 | покрыт |
| `POST /api/simulator/terminal/start` (Gateway) | 500, 503 | 2 | покрыт |
| `POST /api/simulator/terminal/start-continuous` (Gateway) | 200, 422, 503 | 3 | покрыт |
| `GET /api/simulator/terminal/status` (Gateway) | 500, 503 | 2 | покрыт |
| `POST /api/simulator/terminal/stop` (Gateway) | 200, 503 | 2 | покрыт |
| `GET /api/transactions` (Gateway) | 404 | 1 | покрыт |
| `POST /api/transactions` (Gateway) | 200, 400, 415, 429, 503 | 66 | покрыт |
| `GET /api/transactions/export` (Gateway) | 200, 400, 503 | 3 | покрыт |
| `GET /api/transactions/search` (Gateway) | 200, 400, 503 | 44 | покрыт |
| `POST /api/transactions/search` (Gateway) | 405 | 1 | покрыт |
| `GET /cards-docs` (Gateway) | 200, 500 | 2 | покрыт |
| `GET /docs` (Gateway) | 302 | 1 | покрыт |
| `GET /health` (Gateway) | 200, 503 | 2 | покрыт |
| `GET /logger-docs` (Gateway) | 200, 500 | 2 | покрыт |
| `GET /merchant-docs` (Gateway) | 200, 500 | 2 | покрыт |
| `GET /switch-docs` (Gateway) | 200, 500 | 2 | покрыт |
| `GET /terminal-docs` (Gateway) | 200, 500 | 2 | покрыт |
| `GET /v3/api-docs` (Gateway) | 200 | 1 | покрыт |
| `GET /ws/transactions` (Gateway) | 404 | 1 | покрыт |

### Перечень эндпоинтов из трёх источников

Источники: спецификация (`docs/api-spec.md`, `docs/api/openapi.yaml`), контроллеры сервисов, маршруты Gateway (`application.yml`).

| Эндпоинт | api-spec.md | openapi.yaml | Контроллер | Маршрут Gateway | Расхождение |
|---|---|---|---|---|---|
| `GET /health` | да | да | да | контроллер Gateway | - |
| `POST /api/cards`, `GET /api/cards/{pan}`, `POST /api/cards/generate` | да | да | да | да | - |
| `GET /api/cards`, `PATCH`, `DELETE /api/cards/{pan}`, `POST .../reserve`, `.../rollback` | нет | да | да | да | api-spec.md неполон |
| `POST /api/transactions` | да | да | Switch: `/api/internal/route` | да, с переписыванием пути | - |
| `GET /api/transactions/search` | да | да | да | да | D-02: фильтры issuerId и mcc только в openapi.yaml |
| `GET /api/transactions/export` | нет | да | да | да | api-spec.md неполон |
| `POST /api/simulator/terminal/run` | да | да | да | да | D-04 |
| `POST /api/simulator/terminal/start`, `GET .../status` | нет | да | нет | попадают в маршрут | D-05 |
| `POST /api/simulator/merchant/run` | да | да | да | да | - |
| `GET /api/simulator/merchants` | нет | да | да | нет | D-06 |
| `GET /api/dashboard/stats`, `/recent` | да | да | да | да | D-20 |
| `GET /api/dashboard/charts` | нет | да | да | да | api-spec.md неполон |
| `WS /ws/transactions` | да | да | да, порт Logger | нет | D-06 |
| `POST /api/internal/route`, `/authorize`, `/log` | да | да | да | нет, так и задумано | D-18, D-19 |
| `/docs`, `/v3/api-docs`, `/switch-docs`, `/logger-docs`, `/terminal-docs`, `/merchant-docs`, `/cards-docs` | нет | нет | нет | да | служебные, покрыты; D-36 |
| `POST /api/cards/bulk-update`, `POST /api/simulator/merchant/fee`, `.../terminal/start-continuous`, `.../stop` | нет | нет | да | да | D-21: открыты через Gateway, покрыты |
| `POST /api/internal/rollback`, `GET /api/bin/{bin}` | нет | нет | да | нет | D-21: участвуют в цепочке авторизации, покрыты напрямую |
| `/api/bins`, `GET /api/notifications` | нет | нет | да | нет | D-21: вне Gateway и вне цепочки авторизации, не покрыты |

## 2. Бизнес-отказы

Исходы взяты из `DeclineOutcome` и `AuthorizationResponse`. Код и причина проверяются вместе: код 05 общий у CARD_BLOCKED, CARD_INACTIVE и отказа Switch, код 96 - у RESERVATION_FAILED, SERVICE_UNAVAILABLE и System error.

| Исход | Код | Причина | Путь достижения | Способ подготовки данных | Статус |
|---|---|---|---|---|---|
| Карта не найдена | 14 | CARD_NOT_FOUND | Switch: BIN нет в таблице, `mti=0110`, время 0 | данные запроса: PAN с BIN 999999 | покрыт |
| Карта не найдена | 14 | CARD_NOT_FOUND | Authorization: BIN известен, карты нет, `mti=0100` | данные запроса: PAN с известным BIN | покрыт |
| Карта не найдена | 14 | CARD_NOT_FOUND | карта удалена | состояние: `DELETE /api/cards/{pan}` | покрыт |
| Карта неактивна | 05 | CARD_INACTIVE | статус | состояние: `PATCH status=INACTIVE` | покрыт |
| Карта заблокирована | 05 | CARD_BLOCKED | статус | состояние: `PATCH status=BLOCKED` | покрыт |
| Срок истёк | 54 | CARD_EXPIRED | статус EXPIRED | состояние: `PATCH status=EXPIRED` | покрыт |
| Срок истёк | 54 | CARD_EXPIRED | статус ACTIVE, дата передачи позже срока карты | данные запроса: `transmissionDateTime` первого дня следующего месяца | покрыт, граница тоже |
| Недостаточно средств | 51 | INSUFFICIENT_FUNDS | сумма больше баланса на 1 | данные запроса: `amount` | покрыт |
| Недостаточно средств | 51 | INSUFFICIENT_FUNDS | баланс снижен | состояние: `PATCH availableBalance` | покрыт |
| Превышен лимит | 61 | EXCEEDS_AMOUNT_LIMIT | накопление за день | две транзакции | покрыт |
| Превышен лимит | 61 | EXCEEDS_AMOUNT_LIMIT | накопление за месяц по разным дням | две транзакции с разной `transmissionDateTime` | покрыт, граница тоже |
| Превышен лимит | 61 | EXCEEDS_AMOUNT_LIMIT | лимит снижен | состояние: `PATCH dailyLimit` | покрыт |
| Ошибка резерва | 96 | RESERVATION_FAILED | CMS отвечает 409 на резерв | следующий RRN занимается на карте заранее: RRN - это счётчик | покрыт |
| Сервис недоступен | 96 | SERVICE_UNAVAILABLE | CMS перезапускается | `docker compose restart card-management` | покрыт |
| Authorization недоступен | 05 | Authorization service unavailable | Switch исчерпал 3 попытки, `mti=0110` | `docker compose restart authorization` | покрыт |
| Authorization недоступен | 05 | Authorization service unavailable | ошибка клиента: длина stan (D-14) | данные запроса | покрыт |
| Системная ошибка | 96 | System error | брокер недоступен, одобрение откатывается, `mti=0110` | `docker compose stop rabbitmq` | покрыт |
| Сбой БД лимитов | 96 | DB_UNAVAILABLE | БД недоступна только Authorization | не воспроизводится через API: БД общая с CMS, карта не будет прочитана | вне API-покрытия, есть unit-тест |
| Неизвестная причина | 05 | UNKNOWN_REASON | статус карты null, непредвиденное исключение | не воспроизводится через API | вне API-покрытия, есть unit-тест |
| Откат: не найдено | 14 | TRANSACTION_NOT_FOUND | неизвестный RRN | данные запроса к `/api/internal/rollback` | покрыт |
| Откат: уже выполнен | 05 | ALREADY_ROLLED_BACK | повторный откат | два запроса | покрыт |
| Ошибка отката | 96 | ROLLBACK_FAILED | CMS отвечает на откат нестандартным статусом | CMS такого статуса не возвращает | вне API-покрытия, есть unit-тест |

Разные пути одного исхода отличаются тем, какая проверка сработала. Статус EXPIRED - это значение в БД, просроченная дата - сравнение `expiryDate` с датой из запроса. Первый путь защищает от ошибки в проверке статуса, второй - от ошибки в проверке срока, и один не страхует другой. Так же устроены CARD_NOT_FOUND (решение Switch или Authorization, видно по `mti`) и EXCEEDS_AMOUNT_LIMIT (накопление или изменение лимита).

Порядок проверок покрыт в части баланса и лимитов: баланс проверяется раньше (D-12).

## 3. Валидация

Основной набор - `POST /api/transactions`, правила `TransactionRequestValidator` и `TransactionValidationFilter`. На каждое нарушение проверяются статус 400, `error`, точный текст `message`, `serviceName=gateway`, `timestamp`, `retryAfterMs=null`. Значения одного правила собраны в одну таблицу параметров.

| Поле | Правило | Классы | Границы | Кол-во проверок |
|---|---|---|---|---|
| `mti`, `stan`, `pan`, `processingCode`, `currencyCode`, `transmissionDateTime`, `terminalId`, `merchantId`, `mcc`, `acquirerId`, `amount` | обязательно (`terminalType` Gateway не требует) | поле отсутствует - по строке на каждое; `null` и пробелы показаны на `pan` | - | 13 |
| `pan` | ровно 16 цифр | короткий, длинный, буква | 15 и 17 цифр | 3 |
| `mti` | ровно `0100` | другой тип, короче | `100` | 2 |
| `amount` | число больше 0 | ноль, отрицательное, не число | `0`, `1`, сумма равна балансу | 3 |
| `currencyCode` | 3 символа | короче, длиннее | 2 и 4 символа | 2 |
| `mcc` | ровно 4 цифры | короче, длиннее, буква | 3 и 5 символов | 3 |
| `transmissionDateTime` | ISO 8601 со смещением | без Z, несуществующий месяц | - | 2 |
| тело | JSON-объект | не JSON, массив, `null` | - | 3 |
| `Content-Type` | JSON | text/plain: 415 от Switch | - | 1 |
| `stan`, `currencyCode`, `processingCode`, `terminalType`, `amount` | по контракту формат строже проверок Gateway | значения вне формата | `stan` из 2 символов, `amount` 0.5 | 6 (D-14, D-15) |

Правила других эндпоинтов проверены тем же приёмом: один невалидный класс на запрос, проверяется тело ошибки.

| Эндпоинт | Поля и правила | Кол-во проверок |
|---|---|---|
| `POST /api/cards` | `bin`, `cardholderName`, `currencyCode`, лимиты, обязательность | 12 |
| `GET /api/cards/{pan}`, `PATCH`, `DELETE` | PAN 16 цифр, лимиты, `monthlyLimit` не меньше `dailyLimit`, enum | 7 |
| `GET /api/cards` | `limit` и `offset` с допустимыми границами, `bin`, `issuerId`, enum, `startDate` и `endDate` | 12 |
| `POST .../reserve`, `.../rollback` | `amount` больше 0, RRN ровно 12 цифр, PAN | 10 |
| `POST /api/cards/generate` | границы `count`, формат BIN, неизвестный BIN | 4 |
| `POST /api/internal/authorize`, `/rollback` | `@ExactSize` полей, `issuerId`, RRN, PAN, `amount` | 6 |
| `POST /api/internal/log` | обязательность, формат, enum | 6 |
| `GET /api/transactions/search` | `limit` 1..500, `offset`, enum, дата, обратный диапазон | 6 |
| `GET /api/dashboard/recent`, `/charts` | `limit`, `granularity`, `from`, `to` | 5 |
| Симуляторы | `count`, `tps`, `scenario`, MCC, обязательные поля `/fee` | 8 |
| `POST /api/cards/bulk-update` | ровно одно из `bins` и `pans`, формат PAN | 3 |

## 4. Расхождения контракта и реализации

Классификация: **дефект** - верен контракт, чинить код; **документация** - верен код, чинить спецификацию; **ограничение** - принято осознанно. Значимость оценена для потребителя API.

| № | Декларировано | Наблюдается | Источник декларации | Классификация |
|---|---|---|---|---|
| D-01 | `mti` ответа `0110` | ответы Authorization возвращают `0100`, ответы Switch `0110`. Потребитель не может полагаться на поле | api-spec.md, openapi.yaml | дефект, средняя |
| D-02 | у поиска нет фильтров `issuerId`, `mcc` | фильтры работают | api-spec.md (в openapi.yaml есть) | документация, низкая |
| D-03 | `expiryDate` в формате MMYY | `2029-10`, то есть YYYY-MM | api-spec.md, openapi.yaml | дефект контракта, средняя: схема Card ответу не соответствует |
| D-04 | пример `{"count": 50, "scenario": "normal"}` | 400: обязателен `tps`, которого нет в спецификации | api-spec.md, openapi.yaml | дефект, высокая: пример из документации не работает |
| D-05 | `POST /terminal/start` (202) и `GET /terminal/status` | эндпоинтов нет, ответ 500 вместо 404. В коде вместо них `start-continuous` и `stop` | openapi.yaml | документация и дефект обработки ошибок, средняя |
| D-06 | `GET /api/simulator/merchants` и `WS /ws/transactions` на базовом URL Gateway | 404: маршрут `/api/simulator/merchant/**` не совпадает, для WebSocket маршрута нет | openapi.yaml, api-spec.md | дефект, средняя |
| D-07 | ошибка клиента - 400 | CMS отвечает 500 на битый JSON, неизвестный enum, некорректную дату. Через Gateway такие 5xx считаются сбоем и размыкают circuit breaker | openapi.yaml, лекция 9 | дефект, высокая |
| D-08 | обязательны только `bin` и `cardholderName`, остальное имеет default | без `currencyCode` - 400; без лимитов - 400 `OutOfRetriesException` с текстом про коллизию PAN | openapi.yaml | дефект, средняя |
| D-09 | откат возвращает зарезервированную сумму | сумма из запроса не сверяется: резерв 100, откат 999, баланс вырос на 899 | openapi.yaml, смысл операции | дефект, критическая |
| D-10 | откат снимает запись из `limit_usage`, лимит считают только успешные авторизации | после отката, отказа брокера и RESERVATION_FAILED лимит остаётся занятым | tz/04 | дефект, высокая |
| D-11 | удалённая карта не возвращается, ответ отражает состояние карты | кеш Gateway живёт 5 минут: после транзакции отдаётся старый баланс, после `DELETE` - удалённая карта с 200. Кеш чистится только на 200 и 201, а `DELETE` отвечает 204 | openapi.yaml | дефект, высокая |
| D-12 | порядок: лимиты, затем баланс | баланс проверяется первым, тот же запрос получает 51 вместо 61 | tz/04, openapi.yaml | документация, низкая: важен контракт на коды, порядок лучше зафиксировать по коду |
| D-13 | ключа идемпотентности в пути авторизации нет, риск назван в лекции 10 | повтор запроса с тем же STAN списывает деньги второй раз | лекция 10 | дефект, высокая |
| D-14 | `stan`, `processingCode` 6 цифр, `currencyCode` 3 цифры, `terminalType` enum, `acquirerId` до 10 символов | Gateway проверяет только длину `currencyCode`. Короткий `stan` даёт 200 и отказ 05 через 3 секунды; `ABC123`, `ABC`, `KIOSK` проходят до одобрения; длинный `acquirerId` приводит к D-16 | openapi.yaml, TransactionRequest | дефект, высокая |
| D-15 | `amount` целое, минимум 1 | 0.5 одобряется, а баланс в ответе округляется | openapi.yaml | дефект, средняя |
| D-16 | после 3 доставок сообщение уходит в DLQ, «ни одна транзакция не будет APPROVED без записи» | аргумент `x-max-delivery` RabbitMQ не поддерживает. Сообщение, которое Logger не может сохранить, крутится бесконечно (Logger занимает 80% CPU), DLQ пуст, деньги списаны, записи нет | docs/architecture.md, лекция 10, tz/03 | дефект, критическая |
| D-17 | ErrorResponse: обязательны `error`, `message`, `timestamp`; `retryAfterMs` целое | 429 и 503 без `timestamp`, 429 без `serviceName`; в Logger `retryAfterMs` строка `"0"`; у Switch и Authorization на 400 формат Spring без имени поля; в CMS `error` - имя класса исключения, а часть сообщений поле не называет: `must be greater than 0` у `amount`, `Value can not be negative` у лимитов, `must be greater than or equal to 1` у `count`; мерчант при недоступном Gateway отвечает 502 с `serviceName=Card management` | openapi.yaml, практика 5: из ошибки должно следовать поле и правило | дефект, средняя |
| D-18 | `/api/internal/authorize` отвечает только 200 | 403, 404, 422, 400, 503. Switch гасит их, разбирая тело | openapi.yaml, лекция 10 подтверждает код | документация, низкая |
| D-19 | повтор в Logger - 200 с `LogResponse`, `status=already_exists` | 200 с полной транзакцией | openapi.yaml (tz/08 подтверждает код) | документация, низкая |
| D-20 | `/recent`: `limit` до 100 | принимается до 500 | openapi.yaml | документация, низкая |
| D-21 | эндпоинт объявлен в спецификации | не описаны `bulk-update`, `/api/bins`, `rollback`, `fee`, `start-continuous`, `stop`, `/api/bin/{bin}`, `/api/notifications`. `/fee` и `bulk-update` открыты через Gateway. Покрыты все, кроме `/api/bins` и `/api/notifications`: они не открыты наружу и не участвуют в цепочке | api-spec.md, openapi.yaml | документация, средняя |
| D-22 | Authorization недоступен: 503 с `serviceName`. CMS недоступен: 05 `ISSUER_TIMEOUT` | Authorization: 200 и 05. CMS: 200 и 96 `SERVICE_UNAVAILABLE` | openapi.yaml, tz/04 | документация, средняя: источники спорят между собой, tz/03 совпадает с кодом |
| D-23 | неподдерживаемый метод - 405 | `GET`, `PUT`, `DELETE`, `PATCH /api/transactions` дают 404 от Logger: маршрут `/api/transactions/**` | лекция 9 | дефект, низкая |
| D-24 | перечень статусов и правил | не объявлены 413 у `GET /api/cards`, 404 у `generate`, 409 у `reserve`; RRN описан как `maxLength: 12`, а код требует ровно 12 цифр | openapi.yaml | документация, средняя |
| D-25 | ошибка называет поле и правило | `dateFrom > dateTo`: 400 с пустым `message` | практика 5, openapi.yaml | дефект, средняя |
| D-26 | примеры содержат `TERM001` и `MERCH12345678901`, `merchantId` до 15 символов | пример нарушает собственный формат, Switch молча меняет значения: `TERM0010`, `MERCH1234567890` | api-spec.md | документация, средняя: в журнале не то, что прислал клиент |
| D-27 | PATCH запрещает `monthlyLimit < dailyLimit` | POST это принимает; отрицательный баланс принимают и POST, и PATCH | код PATCH как образец | дефект, средняя |
| D-28 | `CardUpdateRequest.status`: ACTIVE, INACTIVE, BLOCKED | EXPIRED принимается | openapi.yaml | документация, низкая |
| D-29 | единый текст ошибки 503 | первый отказ `Switch service...`, при открытой цепи `switch service...`; `Retry-After` есть только при открытой цепи | openapi.yaml | дефект, низкая |
| D-30 | лимит 100 запросов в секунду | лимит на IP из `X-Forwarded-For`, заголовок не проверяется, поэтому лимит обходится его сменой. Тест на этом и построен | tz/02, комментарий в ClientIpResolver | ограничение: стенд без обратного прокси |
| D-31 | ошибка клиента - 4xx | `/api/notifications?size=0` даёт 500 | лекция 9 | дефект, низкая, найдено при разведке |
| D-32 | openapi.yaml: Switch «синхронно пишет» в Logger; tz/03: откат через `mti=0400`, ожидание подтверждения 2 секунды | запись через RabbitMQ, откат через `POST /api/internal/rollback`, `LoggerClient` подтверждение брокера не ждёт. При остановленном брокере клиент ждал ответа до 39 секунд, а не 2 | openapi.yaml, tz/03 | документация, средняя; ожидание подтверждения - дефект |
| D-33 | `GET /api/cards`: `limit` по умолчанию 50 | 10, как в openapi.yaml | tz/05 | документация, низкая |
| D-34 | RRN `{YDDD}{HH}{mm}{ss}{seq}` | год, день года и счётчик. Следующий RRN предсказуем, на этом построен тест RESERVATION_FAILED | openapi.yaml, tz/04 | документация, низкая |
| D-35 | недоступный сервис - 503 | при `docker stop` адрес контейнера исчезает, `NoRouteToHostException` не считается недоступностью: запросы висят от 3 до 15 секунд и дольше, затем 500, circuit breaker не срабатывает. 503 приходит только на `connection refused`. Проверено вручную, автотест отсутствует, потому что время ответа нестабильно | openapi.yaml, tz/02 | дефект, высокая |
| D-36 | недоступный сервис - 503 с `serviceName` | у маршрутов `*-docs` нет `metadata.serviceName`: при отказе сервиса Gateway отдаёт 500 в формате Spring, circuit breaker на них не действует | openapi.yaml, tz/02 | дефект, низкая: затронута только документация |
| D-37 | запрос маршрута уходит в сервис маршрута (`application.yml` Gateway) | после одновременного рестарта `terminal-simulator` и `merchant-acquirer` контейнеры обменялись IP, а Gateway продолжил ходить по старым адресам: запросы терминала получал мерчант (404 и 500) до перезапуска Gateway. Разрешённый адрес не обновляется. Найдено красным прогоном, автотест отсутствует: раздачу адресов Docker не воспроизвести детерминированно | application.yml Gateway, tz/02 | дефект, высокая: запрос молча уходит чужому сервису |
| D-38 | запись журнала `Transaction` = `AuthorizationRequest` + `AuthorizationResponse`, то есть с `responseCode` | Logger `responseCode` не хранит и в поиске не возвращает: по журналу виден только `status` и `declineReason`, ISO-код отказа потерян | openapi.yaml | дефект, средняя: разбор отказа по журналу опирается только на текст причины |
| D-39 | graceful shutdown: drain-period 30 секунд (`gateway.shutdown.drain-period`, `GATEWAY_STOP_GRACE_PERIOD=35s`), новые запросы получают 503 с `Retry-After` от `GracefulShutdownFilter` | после SIGTERM 503 «Gateway is shutting down, retry later» пришёл только на первый запрос по уже открытому keep-alive соединению. Новые соединения сразу сбрасываются, процесс завершается через 2,5 секунды. Проверено вручную, автотест отсутствует: окно меньше 0,2 секунды | application.yml Gateway, .env.example | дефект, средняя: клиенты при выкатке получают обрыв соединения вместо 503 |

## 5. Обоснование достаточности

**Классы входных данных.** Перечень исходов взят не из спецификации, а из кода: все значения `DeclineOutcome` и фабрики `AuthorizationResponse`. Перечень правил валидации - все условия `TransactionRequestValidator` и все аннотации DTO. Перечень статус-кодов - все ветки `GlobalExceptionHandler` сервисов, `AuthControllerImpl` и фильтры Gateway. Ответы Gateway при отказе сервиса (503 и 500 у `*-docs`) проверены на каждом маршруте: они зависят от `metadata.serviceName` маршрута. Для каждого правила выделены классы (отсутствует, null, пусто, неверный формат, граница) и значения по обе стороны границы.

**Подтверждение полноты.**
- Эндпоинты: три источника сведены в таблицу раздела 1, журнал `CoverageFilter` показывает фактический вызов каждого.
- Комбинации фильтров поиска: попарное покрытие из PICT, модель `pairwise/search-filters.model`, 13 строк вместо 324 полного перебора. Фильтры карт: 9 строк вместо 27. Границы дат поиска проверены отдельно: 00:00:00 и 23:59:59 своих суток UTC против соседних.
- Объём: одно правило или класс - один метод, значения идут в таблицу параметров.
- Способность падать: раздел 6.
- Стабильность: три прогона `make api-tests` подряд на стеке, поднятом с нуля (`docker compose down -v`), по 300 запусков без падений.

**Что оставлено вне покрытия.**

| Что | Почему |
|---|---|
| `/api/bins`, `GET /api/notifications` | не открыты через Gateway и не участвуют в цепочке авторизации и логирования, то есть не входят в требование 1 |
| DB_UNAVAILABLE, UNKNOWN_REASON, ROLLBACK_FAILED | через API недостижимы, покрыты unit-тестами `AuthServiceImplTest` из модуля 4 |
| Отказ PostgreSQL: 503 Logger, 500 CMS, `DataIntegrityViolation` | взаимодействие с БД в контейнерах относится к модулю 6 |
| 502 терминала (`RestClientException`) | возникает, только если CMS ответит 5xx на `GET /api/cards`, а этот запрос через API в 5xx не перевести. 503 терминала и 502 мерчанта проверены |
| 503 `GracefulShutdownFilter` при остановке Gateway | ответ достаётся только запросу по открытому соединению в первые доли секунды (D-39), автотест был бы нестабилен. Проверено вручную |
| Прямой доступ к брокеру | исключение одно: `PoisonMessageApiTest` читает число сообщений и чистит очередь через Management API RabbitMQ. Через API СМП зависшее сообщение не увидеть (D-16), а без очистки оно грузит Logger до конца прогона |
| WebSocket | по условию работа ограничена синхронными HTTP-контрактами, проверено только отсутствие маршрута в Gateway |
| 409 при коллизии PAN в `POST /api/cards` | PAN генерируется сервером, клиент не может вызвать коллизию. Заявленный в api-spec.md статус недостижим |
| 402 от CMS в пути авторизации | возможно только при гонке между чтением карты и резервом, детерминированно не воспроизводится. Сам ответ 402 проверен напрямую на `reserve` |
| `count=10000` и экспорт 50 000 строк | создали бы десятки тысяч записей на прогон; граница снаружи (`10001`) проверена |
| Точная скорость rate limit | зависит от быстродействия раннера, проверяются поведение на всплеске и восстановление |
| Распределения симуляторов | случайны на малых объёмах, это задача нагрузочного модуля |
| Контроль доступа | в СМП его нет, лекция 10 |

## 6. Доказательства работоспособности проверок

Сервис пересобирался с изменением, запускались связанные тесты, затем код возвращался. Первая строка - обязательная мутационная проверка идемпотентности.

| Тест | Внесённое изменение | Результат |
|---|---|---|
| `LoggerIdempotencyApiTest` | `TransactionService.store`: поиск существующей записи заменён на `Optional.empty()`, то есть дедупликация отключена | красный, 5 падений из 11: 201 вместо 200 и 409 |
| `TransactionValidationApiTest`, `EndToEndApiTest` | валидатор Gateway: `amount <= 0` заменён на `amount < 0` | красный, 2 падения из 41: 200 вместо 400 |
| `BusinessDeclineApiTest.shouldCompareTransmissionTimeWithExpiryMonth` | Authorization: `isBefore` заменён на `!isAfter`, последний день срока считается просроченным | красный, 1 из 19: DECLINED вместо APPROVED |
| `BrokerDownApiTest.shouldDeclineWithSystemErrorAndReturnFundsWhenPublishFails` | Switch: вызов отката при отказе брокера убран | красный: баланс не вернулся |
| `EndToEndApiTest` | Switch: одобренные транзакции не публикуются | красный, 2 из 3, в том числе тест отсутствия эффекта: контрольная транзакция не появляется |

Красные прогоны находили и реальные расхождения: 500 вместо 503 при `docker stop` (D-35), запросы терминала, ушедшие мерчанту после одновременного рестарта симуляторов (D-37).

## 7. Порядок запуска

Одной командой: стек поднимется, скрипт дождётся ответов `/health`, затем запустятся тесты.

```bash
make api-tests
```

Точечный запуск на уже поднятом стеке:

```bash
mvn -f services/pom.xml -Papi-tests test -Dtest='BusinessDeclineApiTest'
```

- Нужны Docker и Maven. `make test` модуль не запускает: ему нужен поднятый стек.
- Дашборд не поднимается: тестам он не нужен, а его порт 3000 часто занят.
- Тесты отказа останавливают контейнеры стека через `docker compose`, поэтому запускать набор параллельно с другой работой на этом стеке нельзя.
- Адреса переопределяются свойствами: `-Dapi.gateway.url=...`.
- CI: `.github/workflows/api-tests.yml`, запускается на push в `practice-5-*` и `main` при изменении сервисов, а также вручную. Отчёты surefire выкладываются артефактом `api-test-reports`.
