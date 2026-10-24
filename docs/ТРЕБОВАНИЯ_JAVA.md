> Технический документ кейса от организаторов (получен через HR, сб 24.10.2026, 15:36). Исходник: `Хакатон_Java_Blueprints_—_требования_к_поддержке_Java.docx`. Текст перенесён без изменений.

# Требование к поддерживаемым конструкциям и структурам языка
| Уровень | Что входит | Роль в оценке |
|---|---|---|
| Минимум | Разделы 1–4 | Обязателен для всех команд |
| Расширенный | Пользовательские типы, дженерики, лямбды, Stream API и др. | Бонусные баллы |
| Вне рамок | Многопоточность, рефлексия, файлы, сеть, java.time | Не оценивается |

## 1. Базовые конструкции языка (java.lang)

Сами конструкции языка не требуют подключения пакетов: они реализуются как узлы графа.

### Типы данных
- Примитивы: int, long, double, float, short, byte, char, boolean
- Обёртки: Integer, Long, Double, Float, Short, Byte, Character, Boolean, Number
- Массивы: одномерные и многомерные

### Операторы
- Арифметические, логические, побитовые, операторы сравнения
- Тернарный оператор, instanceof, приведение типов
- Автоупаковка и распаковка
- Конкатенация строк

### Поток выполнения
- Ветвление: if/else, switch
- Циклы: for, for-each, while, do-while
- Управление циклом: break, continue
- Возврат из метода: return
- Обработка ошибок: try/catch/finally, throw

### Структура программы
- Переменные и константы
- Методы с параметрами и возвращаемым значением
- Вызов методов и конструкторов
- Поля и модификатор static

### Классы-помощники из java.lang

Object, String, StringBuilder, Math, System, Comparable, Iterable, CharSequence, Enum

## 2. Ввод-вывод из консоли
| Пакет | Классы и возможности |
|---|---|
| java.lang | System.out, System.err, System.in |
| java.util | Scanner (nextInt, nextLine, hasNext и др.), Formatter |
| java.nio.charset | StandardCharsets (кодировка консоли, важна для кириллицы) |

Форматированный вывод: String.format и printf.

## 3. Коллекции (java.util)
| Интерфейс | Основные реализации |
|---|---|
| Collection, Iterable | Базовые операции: add, remove, contains, size, isEmpty, clear |
| List | ArrayList, LinkedList |
| Set | HashSet, LinkedHashSet, TreeSet (SortedSet, NavigableSet) |
| Map | HashMap, LinkedHashMap, TreeMap (SortedMap, NavigableMap), Map.Entry |

Вспомогательные классы: Iterator, ListIterator, Collections, Arrays, Comparator, Objects, Optional.

Фабрики неизменяемых коллекций: List.of, Set.of, Map.of.

## 4. Исключения

### Пакет java.lang
- Базовые: Throwable (методы getMessage, getCause, getStackTrace, printStackTrace, addSuppressed), Exception, RuntimeException, Error
- Часто встречающиеся: IllegalArgumentException, IllegalStateException, NullPointerException, ArithmeticException, ClassCastException, NumberFormatException, UnsupportedOperationException
- Индексы и размеры: IndexOutOfBoundsException, ArrayIndexOutOfBoundsException, StringIndexOutOfBoundsException, NegativeArraySizeException
- Прочие: InterruptedException, CloneNotSupportedException
- Ошибки: StackOverflowError, OutOfMemoryError, AssertionError

### Пакет java.util

NoSuchElementException, InputMismatchException (бросается Scanner), ConcurrentModificationException, EmptyStackException

### Пакет java.io

IOException, FileNotFoundException, UncheckedIOException

### Требования к механизму исключений
- Поддержка проверяемых и непроверяемых исключений (throws, обязательный catch)
- Создание собственных исключений через наследование

## 5. Расширенный уровень (бонусные баллы)
- Пользовательские классы и интерфейсы, record, enum
- Дженерики
- Лямбды и java.util.function
- java.util.stream: Stream, Collectors

## 6. Вне рамок кейса

Следующие возможности не требуются и не оцениваются:
- Многопоточность (java.util.concurrent)
- Рефлексия
- Файловый ввод-вывод (java.nio.file)
- Сетевое взаимодействие
- Работа со временем (java.time)