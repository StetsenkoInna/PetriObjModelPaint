<div align="center">

# PetriObjModelPaint

**Графічний редактор і симулятор мереж Петрі та Петрі-об'єктних моделей.**

Малюйте мережу або обведіть частини рисунка рамками Петрі-об'єктів, щоб скласти з них більшу
модель, запускайте з живою анімацією, дивіться статистику та обмінюйтесь моделлю як PNML.

[![License](https://img.shields.io/badge/license-MIT_%2F_PolyForm_NC-1f6feb?style=flat-square)](#ліцензія)
[![PNML](https://img.shields.io/badge/PNML-ISO%2FIEC_15909--2-2ea043?style=flat-square)](docs/petri-object-models.md)
[![petri-net-sim](https://img.shields.io/badge/petri--net--sim-web_app-2563eb?style=flat-square&logo=data:image/svg%2Bxml%3Bbase64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAzMiAzMiI+PGNpcmNsZSBjeD0iMTYiIGN5PSIxNiIgcj0iMTEiIGZpbGw9Im5vbmUiIHN0cm9rZT0iIzI1NjNlYiIgc3Ryb2tlLXdpZHRoPSI0Ii8+PGNpcmNsZSBjeD0iMTYiIGN5PSIxNiIgcj0iNC41IiBmaWxsPSIjMjU2M2ViIi8+PC9zdmc+&logoColor=white)](https://github.com/sergiorbk/petri-net-sim)

![Java 23](https://img.shields.io/badge/Java_23-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-C71A36?style=flat-square&logo=apachemaven&logoColor=white)

![Малювання та симуляція мережі Петрі](docs/media/demo-petri-model.gif)

</div>

---

> [!TIP]
> **Просто хочете скористатися застосунком?** Завантажте готовий редактор без збірки з джерел:
> [Windows](https://github.com/StetsenkoInna/PetriObjModelPaint/releases/download/v2.3.0/petri-swing-ui-2.3.0-windows.zip) ·
> [Linux](https://github.com/StetsenkoInna/PetriObjModelPaint/releases/download/v2.3.0/petri-swing-ui-2.3.0-linux.zip) ·
> [macOS](https://github.com/StetsenkoInna/PetriObjModelPaint/releases/download/v2.3.0/petri-swing-ui-2.3.0-macos.zip)
> (або перегляньте сторінку [останнього релізу](https://github.com/StetsenkoInna/PetriObjModelPaint/releases/latest)
> для інших версій). Розпакуйте і запустіть лаунчер усередині (`.bat` / `.sh` / `.command`): він
> перевіряє наявність Java 23+ і підкаже, звідки її завантажити, якщо її немає.

> [!NOTE]
> **Хочете допомогти проєкту?** Спершу відкрийте issue, щоб обговорити зміну, потім
> створіть гілку, оформіть pull request у `master` і дотримуйтесь кроків з
> [CONTRIBUTING.md](CONTRIBUTING.md).

Це багатомодульний Maven-проєкт:

| Модуль | Призначення |
|--------|------------|
| `petri-math` | Математичне ядро симуляції |
| `petri-api` | Інтерфейси та DTO (спільний контракт) |
| `petri-model` | Граф-модель, парсер PNML |
| `petri-swing-ui` | Десктопний редактор (Swing, fat JAR) |
| `petri-server` | Spring Boot REST + WebSocket сервер |

---

## Два способи запуску

| | |
|---|---|
| **Десктопний UI (Swing)** | Повністю самодостатній візуальний редактор і симулятор, сервер не потрібен |
| **Сервер (Spring Boot)** | REST + SSE + WebSocket API для запуску симуляцій із зовнішніх систем |

Обидва працюють з тією самою моделлю під капотом: чи це одна мережа, чи кілька, складених у
Петрі-об'єктну модель.

**Десктопний UI.**

```bash
mvn package -DskipTests
```

```bash
java -jar petri-swing-ui/target/petri-swing-ui.jar
```

Малюєте мережу, запускаєте з живою анімацією, дивитесь графіки статистики, зберігаєте мережі в
бібліотеку та робите імпорт/експорт PNML. Частини рисунка обводяться рамками Петрі-об'єктів,
зв'язуються дугами через межі рамок, і вся композиція анімується на одному полотні.

![Композиція та запуск Петрі-об'єктної моделі](docs/media/demo-petri-object-model.gif)

**Сервер.**

```bash
mvn package -DskipTests
```

```bash
java -jar petri-server/target/petri-server.jar
# або: mvn spring-boot:run -pl petri-server
```

Стартує на `http://localhost:8080`, інтерактивна документація на `http://localhost:8080/docs`.
`/api/v1` виконує одну мережу, `/api/v2` виконує Петрі-об'єктну модель зі статистикою по кожному
об'єкту.

---

## Технологія Петрі-об'єктного моделювання

PetriObjModelPaint є реалізацією техніки Петрі-об'єктного моделювання (Petri-object simulation technique). Її основна ідея: швидко і гнучко компонувати код моделі складної дискретно-подієвої системи, одночасно забезпечуючи швидке виконання симуляції. Опис поведінки моделі ґрунтується на стохастичній багатоканальній мережі Петрі, а композиція моделі ґрунтується на об'єктно-орієнтованій технології. Програмне забезпечення Петрі-об'єктного моделювання надає масштабовний алгоритм симуляції, графічний редактор, коректне перетворення графічних зображень у модель та коректні результати симуляції.

У коді ця техніка реалізована в модулі `petri-math` (`PetriObjModel`, `PetriSim`, `PetriP`, `PetriT`, `NetLibrary`): окремий Петрі-об'єкт будується класом `PetriSim` з мережі Петрі, причому одна мережа, розроблена в графічному редакторі та збережена в бібліотеці мереж, використовується для створення цілої групи Петрі-об'єктів, як з одними й тими ж параметрами, так і з іншими, переданими в конструкторі Петрі-об'єкта. Далі кілька Петрі-об'єктів компонуються в модель через оголошення зв'язків між ними: спільна позиція двох об'єктів або перехід одного об'єкта, що подає токени в позицію іншого. Коли список Петрі-об'єктів підготовлено, а зв'язки визначено, модель збирається класом `PetriObjModel`, метод `go(double time)` якого запускає симуляцію. Модуль `petri-model` тримає ту саму модель на рівні графа, тому композицію можна намалювати в редакторі, зберегти одним PNML-документом і відтворити на сервері.

---

## Вимоги

- Java 23+
- Maven 3.9+

---

## Збірка

```bash
mvn package -DskipTests
```

Результат:
- `petri-swing-ui/target/petri-swing-ui.jar`
- `petri-server/target/petri-server.jar`

---

## Документація

| Посібник | Що охоплює |
|----------|------------|
| [Desktop UI](docs/desktop-ui.md) (англійською) | Редактор, керування анімацією, модуль статистики, Петрі-об'єкти на полотні, бібліотека мереж, імпорт/експорт PNML |
| [Petri-object models](docs/petri-object-models.md) (англійською) | Об'єкти та зв'язки, композиція моделі в редакторі, формат PNML, запуск із коду або через HTTP |
| [Server integration](docs/petri-server-integration.md) (англійською) | REST API, SSE-стрімінг, WebSocket/STOMP, керування сесією, вимоги до PNML, API Петрі-об'єктних моделей |

---

## Структура проєкту

```
PetriObjModelPaint/
├── petri-math/        # Ядро симуляції (PetriObj, LibNet, utils)
├── petri-api/         # Інтерфейси та DTO
├── petri-model/       # Граф-модель, PNML, конфіг
├── petri-swing-ui/    # Десктопний Swing-редактор
├── petri-server/      # Spring Boot сервер
└── pom.xml            # Parent POM
```

---

## Веб-застосунок

**[petri-net-sim web app](https://github.com/sergiorbk/petri-net-sim)**:
агентний супер-застосунок для побудови, генерування та симуляції мереж Петрі у
браузері, з AI-функціями в основі. Опишіть виробничу чи чергову систему звичайною
мовою, і AI-агент складе з неї мережу з каталогу готових патернів; далі її можна
редагувати в живому графічному редакторі, запускати стохастичні симуляції та
обмінюватися моделями з цим проєктом через PNML.

---

## Ліцензія

Проєкт ліцензується помодульно (див. файл `LICENSE` у каталозі кожного модуля):

| Модулі | Ліцензія |
|--------|----------|
| `petri-math`, `petri-api`, `petri-model` | [MIT](petri-math/LICENSE) |
| `petri-swing-ui`, `petri-server` | [PolyForm Noncommercial 1.0.0](petri-swing-ui/LICENSE) |

Ядро симуляції та бібліотеки моделі залишаються вільними для будь-якого використання,
зокрема комерційного. Десктопний редактор і сервер симуляції можна використовувати лише
в некомерційних цілях: дозволено особисте використання, дослідження, освіту та використання
некомерційними організаціями. Щодо комерційної ліцензії на ці модулі звертайтеся на
<inna.stetsenko-fiot@edu.kpi.ua> або <sergey24rybak@gmail.com>.
