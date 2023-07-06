# searchEngine
<img src="src/main/resources/static/assets/img/icons/dashboard.svg" width="200" height="200">

Добро пожаловать! Это учебный проект внутреннего поискового движка. Движок предназначен для эффективного индексирования, поиска и анализа сайтов.

## Оглавление
- [Использованные технологии](#использованные-технологии)
- [Использование визуального интерфейса](#использование-визуального-интерфейса)
- [API](#api)
  - [Индексация](#индексация)
  - [Поиск](#поиск)
  - [Статистика](#статистика)
- [Контакты](#контакты)

## Использованные технологии

- <img src="https://simpleicons.org/icons/springboot.svg" width="20" height="20"> Проект реализован на основе **Spring Boot**.
- <img src="https://simpleicons.org/icons/mysql.svg" width="20" height="20"> В качетсве базы данных использована **MySQL**.
- <img src="https://simpleicons.org/icons/hibernate.svg" width="20" height="20"> В качетсве ORM **Hibernate**.

## Использование визуального интерфейса

1. Настройте параметры индексации и поиска через конфигурационный файл **application.yml** (не забудьте указатать логин и пароль базы данных).
2. Запустите программу.
3. В левой части панели перейдите во вкладку "**Managment**" и нажмите на кнопку "**Start indexing**".
4. После окончания процесса индексации статистическая информация станет доступна во вкладке "**Statistics**".
5. Для поиска используйте вкладку "**Search**", при использовании поиска есть возможность использовать органичение на поиск по одному сайту.

## API

### Индексация 
Для индексации используйте следующие endpoint:

```html
GET /api/startIndexing
```
```html
GET /api/stopIndexing
```
```html
POST /api/indexPage/url:URL страницы для добавления в индекс
```

### Поиск

```html
GET /api/search?query=тело запроса&offset=0&limit=10&site=ограничение на поиск внутри определенного сайта
```


### Статистика

```html
GET /api/statistics
```
Статистика выдается в виде JSON:
```JSON
{
    "result": true,
    "statistics": {
        "total": {
            "sites": 4,
            "pages": 133,
            "lemmas": 10278,
            "indexing": false
        },
        "detailed": [
            {
                "url": "https://www.playback.ru",
                "name": "PlayBack.Ru",
                "status": "INDEXED",
                "statusTime": 1688441855518,
                "error": null,
                "pages": 75,
                "lemmas": 2715
            },
        ]
      }
}
```

### Контакты:
email: aldungarov.ar@gmail.com
