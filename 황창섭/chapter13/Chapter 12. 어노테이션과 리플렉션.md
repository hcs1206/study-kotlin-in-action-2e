# Chapter 13. DSL 만들기

- 도메인 특화 언어 만들기
- 수신 객체 지정 람다 사용
- invoke 관례 사용
- 기존 코틀린 DSL 예제

## 13.1 API에서 DSL로: 표현력이 좋은 커스텀 코드 구조 만들기

- 깔끔한 API 정의
    - 코드를 읽는 독자가 어떤 일이 벌어질지 명확하게 이해할 수 있어야 한다. 이름과 개념을 잘 선택하면 이런 목적을 달성할 수 있다.
    - 코드에 불필요한 구문이나 번잡한 준비 코드가 가능한 한 적어야 한다. 이번 장에서 주로 초점을 맞추는 것도 바로 그런 갈결함이다. 깔끔한 API는 언어에 내장된 기능과 거의 구분할 수 없다.

| 일반 구문                             | 간결한 구문                                | 사용한 언어 특성         |
|-----------------------------------|---------------------------------------|-------------------|
| StringUtil.capitalize(s)          | s.capitalize()                        | 확장 함수             |
| 1.to("one")                       | 1 to "one"                            | 중위 호출             |
| set.add(2)                        | set += 2                              | 연산자 오버로딩          |
| map.get("key")                    | map["key"]                            | get 메서드에 대한 관례    |
| file.use({f -> f.read()})         | file.use { it.read() }                | 람다를 괄호 밖으로 빼내는 관례 |
| sb.append("yes"), sb.append("no") | with(sb) { append("yes") append("no") | 수신 객체 지정 람다       |

### 13.1.1 도메인 특화 언어

- DSL: 특정 영역(도메인)에 초점을 맞추고 필요하지 않은 기능을 없앤 도메인 특화 언어
- DSL 예시: SQL, 정규식
- DSL은 범용 프로그래밍 언어와 달리 더 선언적(범용 프로그래밍 언어는 명력적)
- 명령은 각 단계를 순서대로 정확히 기술하지만 선언적 언어의 세부 실행은 언어를 해석하는 엔진에 맡긴다.
- DSL의 단점: 범용 언어로 만든 호스트 애플리케이션과 DSL을 함께 조합하기 어려움
- 코틀린에서는 이런 단점을 해결하기 위해 내부 DSL을 만들 수 있게 해줌

### 13.1.2. 내부 DSL은 프로그램의 나머지 부분과 매끄럽게 통합된다

- 독립적인 문법 구조를 갖는 외부 DSL과는 반대로 내부 DSL은 범용 언어로 작성된 프로그램의 일부며, 범용 언어와 동일한 문법을 사용한다.
- 예제: Customer, Country라는 두 테이블이 있고 각 Customer에는 각자가 사는 나라에 대한 참조가 있는 상황
- 목표: 가장 많은 고객이 살고 있는 나라를 알아내기

```roomsql
SELECT A.name, count(B.id)
FROM COUNTRY A
INNER JOIN CUSTOMER B ON B.ID = A.COUNTRY_ID
GROUP BY A.name
ORDER BY COUNT(B.id) DESC
LIMIT 1
```

```kotlin
(Country innerJoin Customer).slice(Country.name, Count(Customer.id))
    .selectAll()
    .groupBy(Country.name)
    .orderBy(Count(Customer.id), order = SortOrder.DESC)
    .limit(1)
```

### 13.1.3 DSL의 구조

- API에는 존재하지 않지만 DSL에만 존재하는 특징: 구조 또는 문법
- DSL에서 질의를 실행하려면 필요한 결과 집합의 여러 측면을 기술하는 메서드 호출을 조합해야 함
- DSL에서는 여러 함수 호출을 조합해서 연산을 만들며 타입 검사기는 여러 함수 호출이 바르게 조합됐는지를 검사
- 별 생각없이 작성했지만 gradle 빌드 스크립트 의존 관계 정의에서도 DSL 구조

```kotlin
dependencies {
  testImplementation(kotlin("test"))
  implementation("org.jetbrains.exposed:core:0.40.1")
  implementation("org.jetbrains.exposed:dao:0.40.1")
}

// 명령-질의 API에서였다면
project.depencies.add("testImplementation", kotlin("test"))
project.depencies.add("implementation", "org....")
project.depencies.add("implementation", "org....")
```

### 13.1.4 내부 DSL로 HTML 만들기

```kotlin
fun createAnotherTable() = createHTML().table {
    val numbers = mapOf(1 to "one", 2 to "two")
    for ((num, string) in numbers) {
        tr {
            td { +"$num" }
            td { +string }
        }
    }
}
```

```html

<table>
    <tr>
        <td>1</td>
        <td>one</td>
    </tr>
    <tr>
        <td>2</td>
        <td>two</td>
    </tr>
</table>
```

- 코틀린 코드를 사용할 경우 컴파일 에러로 안전성을 보장할 수 있고 동적으로 표를 생성하는 것도 가능

## 13.2 구조화된 API 구축: DSL에서 수신 객체 지정 람다 사용

- 수신 객체 지정 람다는 구조화된 API를 만들 때 도움이 되는 강력한 코틀린 기능

### 13.2.1 수신 객체 지정 람다와 확장 함수 타입

```kotlin
/**
 * 람다를 인자로 받는 buildString() 정의하기
 */
fun buildString(
    builderAction: (StringBuilder) -> Unit // 함수 타입인 파라미터 정의
): String {
    val sb = StringBuilder()
    builderAction(sb) // 람다 인자로 StringBuilder 인스턴스를 넘긴다.
    return sb.toString()
}

fun main() {
    val s = buildString {
        it.append("Hello, ") // it은 StringBuilder 인스턴스를 가리킨다.
        it.append("World!")
    }

    println(s)
}
```

```kotlin
/**
 * 수신 객체 지정 람다를 파라미터로 받는 buildString()
 */
fun buildString(
    builderAction: StringBuilder.() -> Unit // 수신 객체가 지정된 함수 타입의 파라미터를 선언한다.
): String {
    val sb = StringBuilder()
    sb.builderAction() // StringBuilder 인스턴스를 람다의 수신 객체로 넘긴다.
    return sb.toString()
}

fun main() {
    val s = buildString {
        append("Hello, ")
        append("World!")
    }

    println(s)
}
```

- StringBuilder.() -> Unit 과 같은 형태를 수신 객체 타입이라 부르고, 람다에 전달되는 타입 객체를 수신 객체라고 부른다.

### 13.2.3 코틀린 빌더: 추상화와 재상용을 가능하게 해준다

- SQL, HTML을 별도 함수로 분리해 이름 부여가 어렵지만 코틀린 내부 DSL을 사용하면 재사용할 수 있다.

```kotlin
/**
 * 도우미 함수를 사용해 목차가 있는 페이지 만들기
 */
fun buildBookList() = creatHTML().body {
    listWithToc {
        item("The Three-Body Problem", ...)
        item("The Three-Body Problem", ...)
        item("The Three-Body Problem", ...)
    }
  }
```

## 13.3 invoke 관례를 사용해 더 유연하게 블록 내포시키기

- invoke 관례를 사용하면 어떤 커스텀 타입의 객체를 함수처럼 호출할 수 있다.


### 13.3.1 invke 관례를 사용해 더 유연하게 블록 내포시키기

```kotlin
/**
 * 클래스 안에서 invoke 메서드 정의하기
 */
class Greeter(val greeting: String) {
    operator fun invoke(name: String) { // Greeter 안에 invoke 메서드를 정의한다.
        println("$greeting, $name!")
    }
}

fun main() {
    val bavarianGreeter = Greeter("Servus")
    bavarianGreeter("Dmitry") // Greeter 인스턴스를 함수처럼 호출한다
}
```

### 13.3.2 DSL의 invoke 관례: 그레이들 의존관계 선언

```kotlin
dependencies {
  testImplementation(kotlin("test"))
  implementation("org.jetbrains.exposed:core:0.40.1")
  implementation("org.jetbrains.exposed:dao:0.40.1")
}
```

- 위 코드처럼 내포된 블록 구조를 허용하는 한편, 평평한 함수 호출 구조 두 마리의 토끼를 잡고자 함

```kotlin
dependencies.implementation("org.jetbrains.exposed:core:0.40.1")

dependencies {
  implementation("org.jetbrains.exposed:core:0.40.1")
}
```

- 첫 번째 경우는 dependencies 변수에 대해 implementation 메서드를 호출 

## 13.4 실전 코틀린 DSL

### 13.4.1 중위 호출 연쇄시키기: 테스트 프레임워크의 should 함수

```kotlin
/**
 * 코테스트 DSL로 단언문 표현하기
 */

class PrefixTest {
    @Test
    fun testKPrefix() {
        val s = "kotlin".uppercase()
        s should startwith("K")
    }
}
```

```kotlin
/**
 * should 함수 사용하기
 */
infix fun <T> T.should(matcher: Matcher<T>) = matcher.test(this)
```

```kotlin
/**
 * 코테스트 DSL에 사용하기 위한 Matcher 선언하기
 */
interfaceMatcher<T> {
    fun test(value: T)
}

fun startWith(prefix: String): Matcher<String> {
    return object: Matcher<String> {
        override fun test(value: String) {
            if(!value.startsWith(prefix)) {
                throw AssertionError("")
            }
        }
    }
}
```

### 13.4.2 원시 타입에 대해 확장 함수 정의하기: 날짜 처리

```kotlin

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * 날짜 조작 DSL 정의하기
 */

val Int.days: Duration
  get() = this.toDuration(DurationUnit.DAYS)

val Int.hours: Duration
  get() = this.toDuration(DurationUnit.HOURS)


val now = Clock.System.now()
val yesterday = now - 1.days
val later = now + 5.hours
```