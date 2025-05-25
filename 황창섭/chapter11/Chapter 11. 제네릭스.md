# Chapter 11. 제네릭스

- 제네릭 함수와 클래스를 정의하는 방법
- 타입 소거와 실체화된 타입 파라미터
- 선언 지점과 사용 지점 변경
- 타입 별명

## 11.1 타입 인자를 받는 타입 만들기: 제네릭 타입 파라미터

- 코틀린은 자바와 많은 부분 제네릭 사용이 동일
- 인스턴스가 생성될 때 타입 결정
- 단, 코틀린에는 로(raw) 타입이 없다. 자바와 다르게 이전 버전 호환성을 지킬 필요가 없었기에 `ArrayList a = new ArrayList()` 와 같은 코드는 불가능

### 11.1.1 제네릭 타입과 함께 동작하는 함수와 프로퍼티

- `fun <T> List<T>.slice(indices: IntRange) : List<T>` 수신 객체와 반환 타입 모두 List<T>다.
- 호출할 때 타입인자를 명시적으로 지정할 수 있지만 대부분 컴파일러가 타입 추론할 수 있기에 필요 없음

### 11.1.2 제네릭 클래스를 <> 구문을 사용해 선언헌다.

- 자바와 사용법이 동일

### 11.1.3 제네릭 클래스나 함수가 사용할 수 있는 타입 제한: 타입 파라미터 제약

- `fun <T: Number> List<T>.sum(): T` 이처럼 Number와 같이 작성하여 String 과 같은 타입은 sum 메서드 사용 제한
- upper 클래스를 제약하면 T를 해당 값으로 취급할 수 있음(Number 메서드를 사용할 수 있다는 의미)

```kotlin
/**
 * 타입 파라미터에 여러 제약을 가하기
 */
fun <T> ensureTrailingPeriod(seq: T) where T : CharSequence, T : Appendable {
    if (!seq.endsWith('.')) {
        seq.append('.')
    }

}
```

### 11.1.4 명시적으로 타입 파라미터를 널이 될 수 없는 타입으로 표시해서 널이 될 수 있는 타입 인자 제외시키기

- 제너릭을 인스턴스화할 때는 어떤 타입으로 타입 인자를 지정해도 타입을 치환할 수 있다. 상계를 정하지 않은 타입은 Any?를 상계로 정한 파라미터와 같다.

```kotlin
class Processor<T> {
    fun process(value: T) {
        value?.hashCode()
    }
}

val nullableStringProcessor = Processor<String?>()
nullableStringProcessor.process(null)
```

- 널이 될수 없는 타입만 타입 일자로 받으려면 Any 사용

## 11.2 실행 시점 제네릭스 동작: 소거된 타입 파라미터와 실제화된 타입 파라미터

- JVM의 제네릭스는 보통 타입 소거를 사용해 구현 -> 실행 시점에 제네릭 클래스의 인터스턴스에 타입 인자 정보가 들어있지 않다는 뜻

### 11.2.1 실행 시점에 제네릭 클래스의 타입 정보를 찾을 때 한계: 타입 검사와 캐스팅

- 코틀린도 제네릭 타입 인자 정보는 런타입에 지워짐. -> List<String> 객체를 만들어도 실행 시점에는 List로만 볼 수 있다는 뜻
- 이런 제약은 파라미터의 타입 인자에 따라 서로 다른 동작을 해야 하는 함수를 작성하고 싶을 때 문제가 됨

```kotlin
fun readNumbersOrWords(): List<Any> {
    val input = readIn()
    val words: List<String> = input.split(",")
    val numbers: List<Int> = words.mapNotNull { it.toIntOrNull() }
    return numbers.ifEmpty { words }
}

fun printList(l: List<Any>) {
    when (1) {
        // Error: Cannot check for an instance of erased type 오류 발생. 컴파일 에러
        is List<String> -> println("Strings: $l")
        is List<Int> -> println("Integers: $l")
    }
}
```

- 코틀린에서는 타입 인자를 명시하고 제네릭 타입을 사용할 수 없기에 스타 프로젝션 `*` 을 통해 검사

### 11.2.2 실체화된 타입 파라미터를 사용하는 함수는 타입 인자를 실행 시점에 언급할 수 있다

- 타입 소거 제약을 피하는 방법 -> 인라인 함수

```kotlin
/**
 * 실제화된 타입 파라미터를 사용하는 함수 정의하기
 */
inline fun <reified T> isA(value: Any) = value is T // reified로 인해 컴파일 가능
```

```kotlin
/**
 * filterIsInstance를 간단하게 정리
 */
inline fun <reified T> Iterable<*>.filterIsInstance(): List<T> {
    val destination = mutableListOf<T>()
    for (element in this) {
        if (element is T) { // 각 원소가 타입 일자로 지정한 클래스의 인스턴스인지 검사
            destination.add(element)
        }
    }
}
```

### 11.2.3 클래스 참조를 실체화된 타입 파라미터로 대신함으로써 java.lang.Class 파라미터 피하기

- java.lang.Class 타입 인자를 파라미터로 받는 API에 대한 코틀린 어댑터를 구축하는 경우 실체화된 타입 파라미터를 자주 사용
- 표준 자바 API ServiceLoader: `val serviceImpl = ServiceLoader.load(Service::class.java)`
- 실체화된 타입 파라미터 사용: `val serviceImpl = loadService<Service>()`

```kotlin
inline fun <reified T> loadService() {
    return ServiceLoader.load(T::class.java)
}
```

### 11.2.4 실체화된 타입 파라미터가 있는 접근자 정의

```kotlin
inline val <reified T> T.canonical: String
    get() = T::class.java.canonicalName

fun main() {
    println(listOf(1, 2, 3).canonical) // java.util.List
    print(1.canonical) // java.lang.Integer
}
```

### 11.2.5 실체화된 타입 파라미터의 제약

- 실체화된 타입 파라미터를 사용할 수 있는 경우
    - 타입 검사와 캐스팅(is, !is, as, as?)
    - 코틀린 리픅렉션 API(::class)
    - 코틀린 타입에 대응하는 java.lang.Class 를 얻기(::class.java)
    - 다른 함수를 호출할 때 타입 인자로 사용
- 사용할 수 없는 경우
    - 타입 파라미터 클래스의 인스턴스 생성
    - 타입 파라미터 클래스의 동반 객체 메서드 호출
    - 실체화된 타입 파라미터를 요구하는 함수를 호출하면서 실체화하지 않은 타입 파라미터로 받은 타입을 타입 인자로 넘기기
    - 클래스, 프로퍼티, 인라인 함수가 아닌 함수의 타입 파라미터를 reified로 지정하기

### 11.3 변셩은 제너릭과 타입 인자 사이의 하위 타입 관계를 기술

- 변성(variance) 개념은 List<String>과 List<Any> 같이 기저 타입이 같고 타입 인자가 다른 여러 타입이 서로 어떤 관계가 있는지 설명하는 개념

### 11.3.1 변성은 인자를 함수에 넘겨도 안전한지 판단하게 해준다

- List<Any> 타입의 파라미터를 받는 함수에 List<String>을 넘기면 안전한지는 보장할 수 없음

```kotlin
/**
 * String이 Any의 하위 타입이라고 해서 안정성을 보장할 수 없는 케이스
 */
fun printContents(list: MutableList<Any>) {
    list.add(42)
}
```

- 원소 변경, 추가가 없는 경우에는 안전함

### 11.3.2 클래스, 타입, 하위 타입

- 클래스: List, 타입: List<String>
- 타입 A 값이 필요한 모든 장소에 타입 B의 값을 넣어도 문제가 없으면 B는 A의 하위 타입
- 널이 될 수없는 타입 A는 널이 될 수 있는 타입 A? 의 하위 타입이다.
- List<String>은 List<Any> 타입의 하위가 아니라는 사실은 제네릭을 다룰 때 중요
- 무공변(invariant): 서로 다른 두 타입 A와 B에 대해 MutableList<A>가 항상 MutableList<B>의 하위 타입도 아니고 상위 타입도 아닌 경우
- 공변적(covariant) A가 B의 하위 타입이면 List<A>는 List<B>의 하위 타입인 경우

### 11.3.3 공변성은 하위 타입 관계를 유지한다

- 제네릭 클래스가 타입 파라미터에 대해 공변적임을 표시하려면 타입 파라미터 이름 앞에 out을 넣기 `interface Producer<out T> { fun produce(): T }`
- 타입 파라미터를 공변적으로 만들면 함수 정의에 사용한 파라미터 타입과 타입 인자의 타입이 정확히 일치하지 않더라고 그 클래스의 인스턴르를 함수 인자나 반환값으로 사용할 수 있다

```kotlin
open class Animal {
    fun feed() { /* ... */
    }
}

class Herd<out T : Animal> {
    val size: int
        get() = /*... */
            operator
    fun get(i: Int): T { /* ... */
    }
}

class cat : Animal() {
    fun cleanLitter() { /* ... */
    }
}

fun takeCareOfCats(cats: Herd<Cat>) {
    for (i in 0..< cats . size) {
        cats[i].cleanLitter()
    }
    feedAll(cats) // 캐스팅 할 필요가 없다
}
```

### 11.3.4 반고변성은 하위 타입 관계를 뒤집는다

| 공변성                                      | 반공변성                                     | 무공변성                  |
|------------------------------------------|------------------------------------------|-----------------------|
| Producer<out T>                          | Consumer<in T>                           | MutableList<T>        |
| 타입 인자의 하위 타입 관계가 제네릭 타입에서도 유지된다          | 타입 인자의 하위 타입 관계가 제네릭 타입에서 뒤집힌다           | 하위 타입 관계가 성립하지 않는다.   |
| Producer<Cat>은 Producer<Animal>의 하위 타입이다 | Consumer<Animal>은 Consumer<Cat>의 하위 타입이다 |                       |
| T를 아웃 위치에서만 사용할 수 있다.                    | T를 인 위치에서만 사용할 수 있다                      | T를 아무 위치에서나 사용할 수 있다. |

### 11.3.6 스타 프로젝션: 제네릭 타입 인자에 대한 정보가 없음을 표현하고자 * 사용

- 제네릭 클래스의 타입 인자가 어떤 타입인지 정확히 모르거나 타입 인자가 어떤 타입인지가 중요하지 않을 떄 스타 프로젝션 구문을 사용할 수 있다.

### 11.3.7 타입 별명

- 타입 별명을 사용하면 타입에 대해 더 짧은 이름이나 다른 이름을 부여할 수 있다. 
- 타입 별명은 컴파일 시점에 원래의 타입으로 치환되다.