# Тест-кейсы и анализ пирамиды

Карта системы - в [`architecture/`](architecture/)
## Часть (б): новые тест-кейсы

Всего 16 кейсов: unit - 3, api - 4, integration - 6, e2e - 3.

| ID | Связанное требование | Уровень пирамиды | Вид | Предусловие | Шаги | Ожидаемый результат | Источник |
|---|---|---|---|---|---|---|---|
| TC-P3-01 | tz/03 §2: маршрутизация по BIN | unit | позитивный | Таблица `switch.bin-routing` из `application.yml` | Вызвать `RoutingService.getIssuerIdByPan` для PAN с BIN `400000`, `400001`, `400002`, `400003`, `400004` | Возвращаются `ISS001`, `ISS002`, `ISS003`, `ISS004`, `ISS005` | КЭ: каждое значение BIN из таблицы |
| TC-P3-02 | tz/03: откат при сбое публикации | unit | негативный | `RouteService` с моками: `AuthorizationClient.authorize` возвращает `APPROVED` с `rrn`, `LoggerClient.log` возвращает `false` | Вызвать `RouteService.route` | `AuthorizationClient.rollback` вызван один раз с этим `rrn`; результат - `DECLINED`, `96` | Сценарий: ветка `!logged && APPROVED` в `RouteService` |
| TC-P3-03 | tz/05 §2: PATCH лимитов | unit | негативный | Объект `Card` с `dailyLimit=100000`, `monthlyLimit=300000` | 1) `withData` с `monthlyLimit=100000`; 2) `withData` с `monthlyLimit=99999` | 1) карта обновлена; 2) `IllegalArgumentException`: дневной лимит больше месячного | ГЗ: месячный лимит = дневной (ON) и дневной - 1 (OFF) |
| TC-P3-04 | tz/02 §4: rate limit | api | негативный | Gateway запущен, Switch заменён заглушкой | С одного IP отправить подряд 101 валидный `POST /api/transactions` за одну секунду | Первые 100 запросов не получают 429; 101-й - HTTP 429, `error=RATE_LIMIT_EXCEEDED`, `retryAfterMs=1000` | ГЗ: 100 запросов (ON) и 101-й (OFF) |
| TC-P3-05 | tz/02 §5: недоступность downstream | api | негативный | Gateway запущен, Switch недоступен | Отправить валидный `POST /api/transactions` | HTTP 503, `error=SERVICE_UNAVAILABLE`, `serviceName=switch` | Контракт: `DownstreamErrorFilter` |
| TC-P3-06 | tz/04 §2, шаг 2: статус карты | api | негативный | Authorization запущен, CMS заменён заглушкой: `GET /api/cards/{pan}` возвращает карту `BLOCKED` | `POST /api/internal/authorize` с этим PAN | HTTP 403; тело: `DECLINED`, `05`, `CARD_BLOCKED`; заглушка не получила запрос `reserve` | Контракт: `AuthControllerImpl`, состояние карты |
| TC-P3-07 | tz/03 §2: BIN не найден | api | негативный | Switch запущен, Authorization заменён заглушкой | `POST /api/internal/route` с PAN, BIN которого `499999` | HTTP 200; тело: `DECLINED`, `14`, `CARD_NOT_FOUND`; заглушка Authorization не получила запросов | КЭ: BIN вне таблицы маршрутизации |
| TC-P3-08 | tz/05 §5: резервирование | integration | позитивный | Card-Management с PostgreSQL; карта `ACTIVE` с балансом 100000 | `POST /api/cards/{pan}/reserve` с `amount=40000`, `rrn=000000000001`; прочитать таблицы БД | 200; в `reservations` строка со статусом `RESERVED`; `cards.available_balance` = 60000; в `outbox_event` строка `PENDING` с событием резерва | Состояние БД: три записи одной транзакцией |
| TC-P3-09 | tz/03 §3: DLX / DLQ, 3 попытки | integration | негативный | Transaction Logger с RabbitMQ и PostgreSQL; очереди пусты | Опубликовать в `smp.transactions` с ключом `transaction.log` сообщение, которое нельзя разобрать как транзакцию | После 3 неудачных доставок сообщение лежит в `transaction-log-dlq`, очередь `transaction-log` пуста; через 60 с `transaction-log-dlq` пуста | Сценарий: отказ потребителя, топология А5 |
| TC-P3-10 | tz/08 §2: приём и идемпотентность | integration | позитивный | Transaction Logger с RabbitMQ и PostgreSQL | 1) Опубликовать транзакцию с `id=X` в `smp.transactions` / `transaction.log`; 2) опубликовать то же сообщение повторно | После шага 1 в `transactions` появилась строка с `id=X`; после шага 2 строка по-прежнему одна | Состояние БД: доставка из очереди, дубль |
| TC-P3-11 | tz/05: outbox → RabbitMQ → Notification | integration | позитивный | Card-Management, Notification Service, RabbitMQ и PostgreSQL; карта `ACTIVE` | `PATCH /api/cards/{pan}` со `status=BLOCKED`; опрашивать БД до 5 с | Событие в `outbox_event` получает статус `PROCESSED`; в `card_notifications` появляется запись с типом `CardServicePatchEvent` | Сценарий: eventual consistency, топология А5 |
| TC-P3-12 | tz/05: outbox, 3 попытки, затем `FAILED` | integration | негативный | Card-Management с PostgreSQL; RabbitMQ остановлен; карта `ACTIVE` | `PATCH /api/cards/{pan}` со `status=BLOCKED`; подождать 5 с | PATCH возвращает 200, статус карты изменён; событие в `outbox_event` после 3 попыток получает статус `FAILED` | Состояние БД: сбой брокера |
| TC-P3-13 | tz/04 §4: месячный лимит по дням | integration | негативный | Authorization с PostgreSQL; `limit_usage` для PAN пуста | 1) `upsertLimitUsage` на 60000 за 1-е число при `monthlyLimit=100000`; 2) `upsertLimitUsage` на 50000 за 2-е число того же месяца | 1) обновлена 1 строка; 2) обновлено 0 строк, месячный расход остался 60000 - транзакция отклоняется с кодом `61` | Состояние БД: расход за разные дни месяца |
| TC-P3-14 | tz/03 §3, tz/08 §2-3: доставка в журнал | e2e | позитивный | Весь стенд запущен; карта `ACTIVE` с балансом и лимитами в норме | `POST /api/transactions`; затем опрашивать `GET /api/transactions/search?pan=...` до 5 с | Ответ `APPROVED`, `00` приходит сразу; запись с тем же `rrn` и статусом `APPROVED` появляется в журнале в пределах 5 с | Сценарий: eventual consistency, sequence 1 |
| TC-P3-15 | tz/03: откат при недоступном брокере | e2e | негативный | Весь стенд запущен; карта `ACTIVE` с балансом 100000; RabbitMQ остановлен | `POST /api/transactions` на 40000; `GET /api/cards/{pan}`; запустить RabbitMQ; `GET /api/transactions/search?pan=...` | Ответ `DECLINED`, `96`; баланс карты снова 100000; в журнале записи о транзакции нет | Сценарий: sequence 3, reversal |
| TC-P3-16 | tz/04 §2, шаг 2; tz/05 §2 | e2e | позитивный | Весь стенд запущен; карта `ACTIVE` | 1) `PATCH status=BLOCKED`; 2) `POST /api/transactions`; 3) `PATCH status=ACTIVE`; 4) `POST /api/transactions` | 2) `DECLINED`, `CARD_BLOCKED`; 4) `APPROVED`, `00`; обе транзакции есть в журнале | Состояние: переходы `ACTIVE` → `BLOCKED` → `ACTIVE`, диаграмма А6 |

Покрытие путей:

| Путь | Что покрыто | Кейсы |
|---|---|---|
| sync | валидация и rate limit на входе | TC-P3-04, TC-P3-05 |
| sync | маршрутизация по BIN | TC-P3-01, TC-P3-07 |
| sync | лимиты и статус карты | TC-P3-03, TC-P3-06, TC-P3-13 |
| sync | резервирование | TC-P3-08 |
| async | доставка в Logger и идемпотентность | TC-P3-10, TC-P3-14 |
| async | сбой публикации и откат | TC-P3-02, TC-P3-15 |
| async | outbox → Notification | TC-P3-11, TC-P3-12 |
| async | DLQ | TC-P3-09 |
| sync + async | переходы статуса карты | TC-P3-16 |

## Часть (в): распределение тест-кейсов практики 2

Все 86 кейсов из [`docs/practice-2/test-design.md`](../practice-2/test-design.md). В практике 2 они записаны как HTTP-запросы через Gateway, здесь для каждого найден самый низкий уровень, на котором правило можно проверить.

| ID (пр. 2) | Название | Что проверяет тест | Уровень | Обоснование |
|---|---|---|---|---|
| TC-AUTH-01 | Все классы валидные (`ACTIVE`, срок в будущем, сумма в пределах лимитов и баланса) | Сквозной сценарий: одобрение проходит цепочку Gateway → Switch → Authorization → Card-Management и резерв виден в балансе | e2e | Единственный позитивный сценарий, который нужен целиком: результат зависит от четырёх сервисов и БД |
| TC-AUTH-02 | Выход - уникальность RRN | Связь с БД: RRN берётся из последовательности `rrn_seq` | integration | Уникальность держится на `fetchRrnBlock` в PostgreSQL, с моком репозитория её не проверить |
| TC-AUTH-03 | Месяц меньше текущего, год больше | Логика: сравнение срока действия с датой транзакции | unit | Правило в `AuthServiceImpl.authorize` (`expiryDate.atEndOfMonth`), клиент CMS мокается |
| TC-AUTH-04 | Сумма = остаток дневного лимита после расхода (ON) | Связь с БД: накопление дневного расхода и граница лимита | integration | Лимит проверяет SQL `upsertLimitUsage`, расход хранится в `limit_usage` |
| TC-AUTH-05 | Минимальная сумма 1 (ON) и 2 | Логика: минимальная допустимая сумма | unit | Правило `amount > 0` в `TransactionRequestValidator` - класс без зависимостей |
| TC-AUTH-06 | Bin Lookup недоступен (штатная деградация) | Контракт Authorization ↔ Bin Lookup при сбое | api | Нужен сервис с заглушкой Bin Lookup, отвечающей ошибкой: проверяется `BinLookupClient` и то, что ответ не меняется |
| TC-AUTH-07 | Сумма = `dailyLimit` - 1 | Связь с БД: граница дневного лимита | integration | Условие `:amount <= :dailyLimit` находится в SQL `upsertLimitUsage` |
| TC-AUTH-08 | Остаток дневного лимита - 1 | Связь с БД: граница остатка дневного лимита | integration | Остаток считается по строке `limit_usage` в PostgreSQL |
| TC-AUTH-09 | Сумма = `monthlyLimit` - 1 | Связь с БД: граница месячного лимита | integration | Месячная сумма считается подзапросом в SQL `upsertLimitUsage` |
| TC-AUTH-10 | Сумма = баланс - 1 | Логика: сравнение суммы с балансом | unit | Условие `amount.compareTo(availableBalance) > 0` в `AuthServiceImpl`, карта приходит из мока |
| TC-AUTH-11 | `expiryDate` = следующий месяц | Логика: срок действия в следующем месяце | unit | То же правило сравнения дат в `AuthServiceImpl` |
| TC-AUTH-12 | Все три границы ON (d = m = b = сумма) | Связь с БД: дневной и месячный лимит и баланс на границе одновременно | integration | Две из трёх границ проверяет SQL, нужен Authorization с PostgreSQL |
| TC-AUTH-13 | PAN не найден в CMS | Логика: ветка «карта не найдена» | unit | `CardNotFoundException` от мока клиента → `DeclineOutcome.CARD_NOT_FOUND` |
| TC-AUTH-14 | Месяц больше текущего, год меньше | Логика: срок истёк, хотя номер месяца больше текущего | unit | То же правило сравнения дат в `AuthServiceImpl` |
| TC-AUTH-15 | Сумма = `dailyLimit` + 1 (OFF) | Связь с БД: превышение дневного лимита не расходует лимит | integration | Нужна настоящая `limit_usage`: второй запрос проверяет, что отказ ничего не записал |
| TC-AUTH-16 | Остаток дневного лимита + 1 (OFF) | Связь с БД: превышение остатка дневного лимита | integration | Остаток считается по строке `limit_usage` в PostgreSQL |
| TC-AUTH-17 | `amount` = 0 | Логика: нулевая сумма отклоняется | unit | Правило в `TransactionRequestValidator` |
| TC-AUTH-18 | `amount` отрицательный | Логика: отрицательная сумма отклоняется | unit | Правило в `TransactionRequestValidator` |
| TC-AUTH-19 | `pan` из 15 цифр (OFF) | Логика: PAN короче 16 цифр | unit | Регулярное выражение `\d{16}` в `TransactionRequestValidator` |
| TC-AUTH-20 | `pan` из 17 цифр (OFF) | Логика: PAN длиннее 16 цифр | unit | То же регулярное выражение в `TransactionRequestValidator` |
| TC-AUTH-21 | CMS недоступен | Контракт Authorization ↔ Card-Management при недоступности CMS | api | Нужна заглушка CMS с ответом 503: проверяется, как `CardManagementClientImpl` превращает HTTP-ошибку в отказ |
| TC-CMS-01 | Существующий PAN | Контракт `GET /api/cards/{pan}`: 200 и поля карты | api | Проверяется ответ эндпоинта, достаточно одного сервиса с тестовой БД |
| TC-CMS-02 | Каждое значение фильтра `status` | Связь с БД: фильтр по статусу | integration | Фильтр строит SQL-запрос в `CardCriteriaBuilderJpaRepositoryImpl` |
| TC-CMS-03 | `offset` = `total-1`, `total` (ON), `total+1` | Связь с БД: пагинация `limit` / `offset` | integration | Смещение выполняет SQL-запрос, нужны реальные строки в PostgreSQL |
| TC-CMS-04 | `PATCH` одного поля | Контракт `PATCH`: меняется только переданное поле | api | Проверяется запрос и ответ эндпоинта одного сервиса |
| TC-CMS-05 | Валидные `count` и `bins` | Логика генератора: распределение по BIN и диапазоны сумм | unit | `CardGeneratorService.generate`, `CardService` мокается |
| TC-CMS-06 | `count=1` (ON) и `count=2` | Логика генератора: нижняя граница количества | unit | `CardGeneratorService.generate` с 1 и 2 картами |
| TC-CMS-07 | `count=9999` и `count=10000` (ON) | Логика генератора: верхняя граница количества | unit | Сравнение `count > maxCount` в `CardGeneratorService` |
| TC-CMS-08 | Выход генератора - распределение статусов | Логика генератора: доли статусов 95 / 3 / 2 % | unit | Метод `generateStatuses` считает доли арифметикой |
| TC-CMS-09 | `reserve amount=1` (ON) и `amount=2` | Логика: минимальная сумма резерва | unit | Ограничение `@Positive` на `ReserveRequest.amount`, проверяется валидатором без HTTP |
| TC-CMS-10 | `reserve amount` = баланс (ON) | Логика: резерв на весь баланс | unit | `Card.startReservation`: `availableBalance.compareTo(amount) < 0` |
| TC-CMS-11 | Мягкое удаление | Связь с БД: удалённая карта скрыта от запросов | integration | Скрытие делает `@SQLRestriction("status <> 'DELETED'")` на уровне запроса к БД |
| TC-CMS-12 | `cardholderName` из 254 символов | Связь с БД: длина имени у границы колонки | integration | В DTO длина имени не ограничена, предел задаёт только колонка `VARCHAR(255)` |
| TC-CMS-13 | `reserve amount` = баланс - 1 | Логика: резерв на сумму чуть меньше баланса | unit | `Card.startReservation` |
| TC-CMS-14 | `rrn` из 11 символов | Логика: длина RRN | unit | Правило `^\d{12}$` в `RrnValidator` |
| TC-CMS-15 | `bin` из 7 цифр (OFF) | Логика: BIN длиннее 6 цифр | unit | Правило `^\d{6}$` в `BinValidator` |
| TC-CMS-16 | BIN вне таблицы | Контракт `POST /api/cards` для неизвестного BIN | api | Проверяется код ответа: `BinNotFoundException` → HTTP-код в `GlobalExceptionHandler` |
| TC-CMS-17 | Контрольная цифра Луна ±1 (OFF) | Контракт `GET /api/cards/{pan}` для несуществующей карты | api | Проверяется код ответа эндпоинта; `@Pan` проверяет только 16 цифр, не Луна |
| TC-CMS-18 | Фильтр `status` не из списка | Контракт: недопустимое значение фильтра `status` | api | Ошибка возникает при разборе параметра запроса, нужен HTTP-слой |
| TC-CMS-19 | `PATCH status=EXPIRED` | Контракт `PATCH`: статус `EXPIRED` | api | Проверяется, какие значения принимает эндпоинт |
| TC-CMS-20 | `PATCH status=DELETED` | Контракт `PATCH`: статус `DELETED` | api | Ошибка возникает при десериализации `CardModelStatus`, нужен HTTP-слой |
| TC-CMS-21 | `count=0` (OFF) | Логика: количество карт меньше 1 | unit | Ограничение `@Min(1)` на `GenerateCardsRequest.count` |
| TC-CMS-22 | `count=10001` (OFF) | Логика: количество карт больше максимума | unit | `CardGeneratorService`: `count > maxCount` → `CardGenerationLimitException` |
| TC-CMS-23 | Пустой `bins` (структура) | Логика: пустой список BIN | unit | Ограничение `@NotEmpty` на `GenerateCardsRequest.bins` |
| TC-CMS-24 | `reserve amount=0` (OFF) | Логика: нулевая сумма резерва | unit | Ограничение `@Positive` на `ReserveRequest.amount` |
| TC-CMS-25 | `reserve amount` = баланс + 1 (OFF) | Логика: резерв больше баланса | unit | `Card.startReservation` бросает `InsufficientFundsException` |
| TC-CMS-26 | `rrn` из 13 символов (OFF) | Логика: RRN длиннее 12 цифр | unit | Правило `^\d{12}$` в `RrnValidator` |
| TC-PW-AUTH-01 | `INACTIVE`, срок в будущем; дневной лимит ниже, месячный ниже, баланс ниже; day | Логика: отказ по статусу карты | unit | Решение принимает ветка по статусу в `AuthServiceImpl`, клиент CMS мокается |
| TC-PW-AUTH-02 | `EXPIRED`, срок истёк; дневной лимит ниже, месячный ниже, баланс ниже; night | Логика: отказ по статусу карты | unit | Решение принимает ветка по статусу в `AuthServiceImpl`, клиент CMS мокается |
| TC-PW-AUTH-03 | `ACTIVE`, срок в будущем; дневной лимит на границе, месячный на границе, баланс выше; night | Связь с БД: граница лимита в сочетании с другими параметрами | integration | В строке лимит на границе или превышен, а лимит проверяет SQL `upsertLimitUsage` |
| TC-PW-AUTH-04 | `ACTIVE`, срок в текущем месяце; дневной лимит ниже, месячный на границе, баланс на границе; day | Связь с БД: граница лимита в сочетании с другими параметрами | integration | В строке лимит на границе или превышен, а лимит проверяет SQL `upsertLimitUsage` |
| TC-PW-AUTH-05 | `INACTIVE`, срок в текущем месяце; дневной лимит ниже, месячный ниже, баланс ниже; night | Логика: отказ по статусу карты | unit | Решение принимает ветка по статусу в `AuthServiceImpl`, клиент CMS мокается |
| TC-PW-AUTH-06 | `ACTIVE`, срок в текущем месяце; дневной лимит на границе, месячный ниже, баланс на границе; day | Связь с БД: граница лимита в сочетании с другими параметрами | integration | В строке лимит на границе или превышен, а лимит проверяет SQL `upsertLimitUsage` |
| TC-PW-AUTH-07 | `ACTIVE`, срок в текущем месяце; дневной лимит на границе, месячный выше, баланс ниже; day | Связь с БД: граница лимита в сочетании с другими параметрами | integration | В строке лимит на границе или превышен, а лимит проверяет SQL `upsertLimitUsage` |
| TC-PW-AUTH-08 | `BLOCKED`, срок в текущем месяце; дневной лимит ниже, месячный ниже, баланс ниже; night | Логика: отказ по статусу карты | unit | Решение принимает ветка по статусу в `AuthServiceImpl`, клиент CMS мокается |
| TC-PW-AUTH-09 | `ACTIVE`, срок в будущем; дневной лимит выше, месячный ниже, баланс ниже; day | Связь с БД: граница лимита в сочетании с другими параметрами | integration | В строке лимит на границе или превышен, а лимит проверяет SQL `upsertLimitUsage` |
| TC-PW-AUTH-10 | `ACTIVE`, срок в текущем месяце; дневной лимит ниже, месячный ниже, баланс выше; day | Логика: сравнение суммы с балансом | unit | Лимиты в норме, решение принимает условие по балансу в `AuthServiceImpl` |
| TC-PW-AUTH-11 | `BLOCKED`, срок в будущем; дневной лимит ниже, месячный ниже, баланс ниже; day | Логика: отказ по статусу карты | unit | Решение принимает ветка по статусу в `AuthServiceImpl`, клиент CMS мокается |
| TC-PW-AUTH-12 | `ACTIVE`, срок в будущем; дневной лимит ниже, месячный ниже, баланс на границе; night | Логика: сравнение суммы с балансом | unit | Лимиты в норме, решение принимает условие по балансу в `AuthServiceImpl` |
| TC-PW-AUTH-13 | `ACTIVE`, срок в будущем; дневной лимит ниже, месячный выше, баланс ниже; night | Связь с БД: граница лимита в сочетании с другими параметрами | integration | В строке лимит на границе или превышен, а лимит проверяет SQL `upsertLimitUsage` |
| TC-PW-AUTH-14 | `ACTIVE`, срок в текущем месяце; дневной лимит выше, месячный ниже, баланс ниже; night | Связь с БД: граница лимита в сочетании с другими параметрами | integration | В строке лимит на границе или превышен, а лимит проверяет SQL `upsertLimitUsage` |
| TC-PW-AUTH-15 | `EXPIRED`, срок истёк; дневной лимит ниже, месячный ниже, баланс ниже; day | Логика: отказ по статусу карты | unit | Решение принимает ветка по статусу в `AuthServiceImpl`, клиент CMS мокается |
| TC-PW-AUTH-16 | `ACTIVE`, срок истёк; дневной лимит ниже, месячный ниже, баланс ниже; night | Логика: отказ по истёкшему сроку | unit | Сравнение дат в `AuthServiceImpl`, клиент CMS мокается |
| TC-PW-AUTH-17 | `ACTIVE`, срок в текущем месяце; дневной лимит на границе, месячный на границе, баланс ниже; day | Связь с БД: граница лимита в сочетании с другими параметрами | integration | В строке лимит на границе или превышен, а лимит проверяет SQL `upsertLimitUsage` |
| TC-PW-CMS-01 | Создание карты: bin=400004, cardholderName=typical, currencyCode=643, dailyLimit=min_1, monthlyLimit=equal_daily, initialBalance=typical | Контракт `POST /api/cards`: 201 и сохранённые поля | api | Проверяются запрос и ответ эндпоинта, достаточно одного сервиса с тестовой БД |
| TC-PW-CMS-02 | Создание карты: bin=400004, cardholderName=len_255, currencyCode=978, dailyLimit=typical, monthlyLimit=above_daily, initialBalance=zero | Контракт `POST /api/cards`: 201 и сохранённые поля | api | Проверяются запрос и ответ эндпоинта, достаточно одного сервиса с тестовой БД |
| TC-PW-CMS-03 | Создание карты: bin=400000, cardholderName=typical, currencyCode=978, dailyLimit=typical, monthlyLimit=above_daily, initialBalance=typical | Контракт `POST /api/cards`: 201 и сохранённые поля | api | Проверяются запрос и ответ эндпоинта, достаточно одного сервиса с тестовой БД |
| TC-PW-CMS-04 | Создание карты: bin=400000, cardholderName=len_255, currencyCode=978, dailyLimit=min_1, monthlyLimit=equal_daily, initialBalance=typical | Контракт `POST /api/cards`: 201 и сохранённые поля | api | Проверяются запрос и ответ эндпоинта, достаточно одного сервиса с тестовой БД |
| TC-PW-CMS-05 | Создание карты: bin=400000, cardholderName=typical, currencyCode=643, dailyLimit=typical, monthlyLimit=equal_daily, initialBalance=zero | Контракт `POST /api/cards`: 201 и сохранённые поля | api | Проверяются запрос и ответ эндпоинта, достаточно одного сервиса с тестовой БД |
| TC-PW-CMS-06 | Создание карты: bin=400000, cardholderName=len_255, currencyCode=643, dailyLimit=min_1, monthlyLimit=above_daily, initialBalance=zero | Контракт `POST /api/cards`: 201 и сохранённые поля | api | Проверяются запрос и ответ эндпоинта, достаточно одного сервиса с тестовой БД |
| TC-PW-CMS-07 | Создание карты: невалидное поле `currencyCode` (RUB) | Логика: ограничение на поле `currencyCode` | unit | Ограничение `@ExactSize(3)` и `@DigitsOnly` на `CreateCardRequest` проверяется валидатором без HTTP и БД |
| TC-PW-CMS-08 | Создание карты: невалидное поле `cardholderName` (empty) | Логика: ограничение на поле `cardholderName` | unit | Ограничение `@NotBlank` на `CreateCardRequest` проверяется валидатором без HTTP и БД |
| TC-PW-CMS-09 | Создание карты: невалидное поле `initialBalance` (negative) | Контракт `POST /api/cards`: отрицательный баланс | api | На `initialBalance` в DTO нет ограничения, поведение видно только по ответу сервиса |
| TC-PW-CMS-10 | Создание карты: невалидное поле `bin` (40000) | Логика: ограничение на поле `bin` | unit | Ограничение `@Bin` на `CreateCardRequest` проверяется валидатором без HTTP и БД |
| TC-PW-CMS-11 | Создание карты: невалидное поле `cardholderName` (len_256) | Связь с БД: имя длиннее колонки | integration | В DTO длина имени не ограничена, предел задаёт только колонка `VARCHAR(255)` |
| TC-PW-CMS-12 | Создание карты: невалидное поле `initialBalance` (negative) | Контракт `POST /api/cards`: отрицательный баланс | api | На `initialBalance` в DTO нет ограничения, поведение видно только по ответу сервиса |
| TC-PW-CMS-13 | Создание карты: невалидное поле `monthlyLimit` (negative) | Логика: ограничение на поле `monthlyLimit` | unit | Ограничение `@NotNegative` на `CreateCardRequest` проверяется валидатором без HTTP и БД |
| TC-PW-CMS-14 | Создание карты: невалидное поле `bin` (40000) | Логика: ограничение на поле `bin` | unit | Ограничение `@Bin` на `CreateCardRequest` проверяется валидатором без HTTP и БД |
| TC-PW-CMS-15 | Создание карты: невалидное поле `cardholderName` (empty) | Логика: ограничение на поле `cardholderName` | unit | Ограничение `@NotBlank` на `CreateCardRequest` проверяется валидатором без HTTP и БД |
| TC-PW-CMS-16 | Создание карты: невалидное поле `bin` (40000A) | Логика: ограничение на поле `bin` | unit | Ограничение `@Bin` на `CreateCardRequest` проверяется валидатором без HTTP и БД |
| TC-PW-CMS-17 | Создание карты: невалидное поле `bin` (40000A) | Логика: ограничение на поле `bin` | unit | Ограничение `@Bin` на `CreateCardRequest` проверяется валидатором без HTTP и БД |
| TC-PW-CMS-18 | Создание карты: невалидное поле `dailyLimit` (negative) | Логика: ограничение на поле `dailyLimit` | unit | Ограничение `@NotNegative` на `CreateCardRequest` проверяется валидатором без HTTP и БД |
| TC-PW-CMS-19 | Создание карты: невалидное поле `currencyCode` (RUB) | Логика: ограничение на поле `currencyCode` | unit | Ограничение `@ExactSize(3)` и `@DigitsOnly` на `CreateCardRequest` проверяется валидатором без HTTP и БД |
| TC-PW-CMS-20 | Создание карты: невалидное поле `dailyLimit` (negative) | Логика: ограничение на поле `dailyLimit` | unit | Ограничение `@NotNegative` на `CreateCardRequest` проверяется валидатором без HTTP и БД |
| TC-PW-CMS-21 | Создание карты: невалидное поле `monthlyLimit` (negative) | Логика: ограничение на поле `monthlyLimit` | unit | Ограничение `@NotNegative` на `CreateCardRequest` проверяется валидатором без HTTP и БД |
| TC-PW-CMS-22 | Создание карты: невалидное поле `cardholderName` (len_256) | Связь с БД: имя длиннее колонки | integration | В DTO длина имени не ограничена, предел задаёт только колонка `VARCHAR(255)` |

## Часть (г): анализ пропорций

### Сводная таблица

| Уровень | Старые (пр. 2) | Новые (часть б) | Итого | Доля |
|---|---:|---:|---:|---:|
| unit | 46 | 3 | 49 | 48 % |
| api / integration | 39 | 10 | 49 | 48 % |
| e2e | 1 | 3 | 4 | 4 % |
| **Итого** | 86 | 16 | **102** | 100 % |

Внутри строки «api / integration»: api - 21 (17 старых и 4 новых), integration - 28 (22 старых и 6 новых).

### Фактические пропорции

| Уровень | Факт | Ориентир |
|---|---:|---:|
| unit | 48 % | 70 % |
| api / integration | 48 % (api 21 %, integration 27 %) | 20 % |
| e2e | 4 % | 10 % |

Получилась не пирамида, а бокал(?): нижний и средний слой равны, верх узкий.

### Объяснение отклонений

**1. Почему не 70 / 20 / 10.** Причин три, все видны в коде.

- **Лимиты проверяет SQL, а не Java.** Дневной и месячный лимит - это один запрос `upsertLimitUsage` в `LimitUsageRepository`. Поэтому 15 старых кейсов с границами лимитов (7 `TC-AUTH` и 8 `TC-PW-AUTH`) нельзя опустить до unit: с моком репозитория тест проверял бы мок, а не условие `<=`.
- **Часть правил живёт только в БД.** Фильтр и пагинация списка карт - это SQL-запрос, длину имени держателя ограничивает колонка `VARCHAR(255)`, удалённую карту скрывает `@SQLRestriction`, уникальность RRN даёт последовательность `rrn_seq`. Это ещё 7 integration-кейсов.
- **Кейсы практики 2 описывают два сервиса с плотными контрактами.** 17 старых кейсов - это контракты: 15 для эндпоинтов Card-Management (создание карты, PATCH, коды ошибок) и 2 для связи Authorization с CMS и Bin Lookup.

Зато 46 из 86 старых кейсов опустились до unit, хотя в практике 2 все они записаны как HTTP-запросы через Gateway. Проверяемое правило у них лежит в одном классе: `TransactionRequestValidator`, ветки `AuthServiceImpl`, `Card.startReservation`, `CardGeneratorService`, ограничения на DTO.

**2. Почему в микросервисах средний слой больше 20 %.** Логика СМП распределена по связям между сервисами. На диаграмме А2 это 13 синхронных HTTP-связей, 5 сервисов со своей схемой в PostgreSQL и 2 асинхронных потока через RabbitMQ. Unit-тест с моками этих связей не видит. Примеры из кода:

- Authorization отвечает на отказ кодом 403, а Switch читает решение из тела ответа. Совпадают ли ожидания двух сервисов, покажет только api-тест (TC-P3-06).
- Резерв, списание баланса и запись в outbox должны попасть в БД одной транзакцией (TC-P3-08).
- Дойдёт ли сообщение до потребителя и уйдёт ли необрабатываемое сообщение в DLQ, зависит от топологии брокера (TC-P3-09, TC-P3-10).

**3. Почему e2e мало и что туда попало.** Четыре кейса, каждый нельзя проверить ниже:

- TC-AUTH-01 - одобрение через всю цепочку;
- TC-P3-14 - запись появляется в журнале после ответа клиенту;
- TC-P3-15 - откат при недоступном брокере;
- TC-P3-16 - карту заблокировали и разблокировали.

Больше e2e не нужно. Для них поднимается весь стенд из 12 контейнеров, из-за асинхронной доставки результат приходится ждать, а упавший тест не показывает, в каком сервисе дефект. Отдельные ветки тех же сценариев уже проверены ниже: логика отката - unit-кейсом TC-P3-02, отказ по статусу - TC-P3-06.

**4. Риски.**

- **Ice-cream cone нет:** e2e занимает 4 %.
- **Дефицита контрактных проверок нет:** api-кейсов 21, они покрывают Gateway, Switch, Authorization и Card-Management.
- **Unit-слой узкий по сервисам.** Из 49 unit-кейсов 47 относятся к Authorization, Card-Management и валидатору Gateway, на Switch приходится 2, на Transaction Logger и Notification Service - ни одного. 
- **Integration-слой дорогой:** 28 кейсов требуют PostgreSQL или RabbitMQ. Чтобы они не превратились в медленные e2e, их стоит писать на уровне репозитория и слушателя очереди, не поднимая сервис целиком.

### Что показал код про кейсы практики 2

Практика 2 строилась по ТЗ и API-контракту. При чтении кода нашлись места, где реализация отличается, и ожидаемые результаты части кейсов нужно будет уточнить при прогоне:

- В `AuthServiceImpl` баланс проверяется раньше лимитов, а в ТЗ - наоборот.
- Недоступность CMS даёт код `96` и причину `SERVICE_UNAVAILABLE` (TC-AUTH-21 ждёт `05` и `ISSUER_TIMEOUT`).
- `Card.withData` отклоняет PATCH, если месячный лимит меньше дневного. Предусловия вида «d=150 000, m=100 000» через PATCH не выставить.
- `RrnValidator` принимает только 12 цифр: `rrn` из 11 цифр будет отклонён (TC-CMS-14 ждёт 200).
- BIN вне таблицы даёт `BinNotFoundException` и код 404 (TC-CMS-16 ждёт 400).
- `PATCH status=EXPIRED` в коде допустим: `CardModelStatus` содержит это значение (TC-CMS-19 ждёт 400).
- На `initialBalance` и на длину `cardholderName` в `CreateCardRequest` ограничений нет.
