# Chapter 18. 오류 처리와 테스트

- 오류와 예외 발생 시 코드의 동작 제어
- 오류 처리를 구조적 동시성 개념과 연결하는 방법
- 시스템 일부가 실패해도 정상적으로 작동하는 코드를 작성하는 방법
- 동시성 코드를 위한 단위 테스트 작성법
- 테스트 실행 속도를 높이고 세밀한 동시성 제약 조건을 테스트하는 방법
- 터빈 라이브러리를 사용한 플로우 테스트

## 18.1 코루틴 내부에서 던져진 오류 처리

- launch나 async 호출을 try-catch로 감싸도 효과는 없다
- 예외를 올바르게 처리하는 한 가지 방법은 람다 블록안에 try-catch 블록을 넣는 것

```kotlin
fun main(): Unit = runBlocking {
    launch {
        try {
            throw UnsupportedOperationException("Ouch!")
        } catch (u: UnsupportedOperationException) {
            println("Handled $u")
        }
    }
}
```

```kotlin
/**
 * 예외를 던지는 async 코루틴
 */
fun main(): Unit = runBlocking {
        val myDeferredInt: Deferred<Int> = async {
            throw UnsupportedOperationException("Ouch!")
        }
        try {
            val i: Int = myDeferredInt.await()
            println(i)
        } catch (u: UnsupportedOperationException) {
            println("Handled $u")
        }
    }
```

## 18.2 코틀린 코루틴에서의 오류 전파

- 코루틴이 작업을 동시적으로 분해해 처리하는 경우 -> 자식 중 하나의 실패가 전체 실패기에 부모의 실패로 이어짐
- 자식이 실패해도 전체 실패로는 이어지지 않을 때 -> 자식이 부모의 실행을 감독

### 18.2.1 자식이 실패하면 모든 자식을 취소하는 코루틴

- 부모 코루틴의 역할
    - 불필요한 작업을 막기 위해 다른 모든 자식을 취소한다.
    - 같은 예외를 발생시키면서 자신의 실행을 완료시킨다.
    - 자신의 상위 계층으로 예외를 전파한다.

```kotlin
/**
 * 하트비트 코루틴과 예외를 던지는 코루틴 시작하기
 */
fun main(): Unit = runBlocking {
        launch {
            try {
                while (true) {
                    println("Heartbeat!")
                    delay(500.milliseconds)
                }
            } catch (e: Exception) {
                println("Heartbeat terminated")
                throw e
            }
        }
        launch {
            delay(1.seconds)
            throw UnsupportedOperationException()
        }
    }
```

### 18.2.2 구조적 동시성은 코루틴 스코프를 넘는 예외에만 영향을 미친다

- 코루틴 내부에서 예외를 잡으면 예외가 발생되어도 계속 텍스트가 출력됨

```kotlin
/**
 * 코루틴 내부에서 예외 잡기
 *
 */
fun main(): Unit = runBlocking {
        launch {
            try {
                while (true) {
                    println("Heartbeat!")
                    delay(500.milliseconds)
                }
            } catch (e: Exception) {
                println("Heartbeat terminated")
                throw e
            }
        }
        launch {
            try {
                delay(1.seconds)
                throw UnsupportedOperationException()
            } catch (u: UnsupportedOperationExcetion) {
                println()
            }
        }
    }
```

### 18.2.3 슈퍼바이저는 부모와 형제가 취소되지 않게 한다.

- 슈퍼바이저는 자식이 실패하더라도 생존한다.
- 일반 Job을 사용하는 스코프와 달리 슈퍼바이저는 일부 자식이 실패를 보고하더라도 실패하지 않는다

```kotlin
/**
 * 예외가 더 이상 전파되지 않게 슈퍼바이저 스코프 사용하기
 */
fun main(): Unit = runBlocking {
        supervisorScope {
            launch {
                try {
                    while (true) {
                        println("Heartbeat!")
                        delay(500.milliseconds)
                    }
                } catch (e: Exception) {
                    println("Heartbeat terminated")
                    throw e
                }
            }
            launch {
                try {
                    delay(1.seconds)
                    throw UnsupportedOperationException()
                } catch (u: UnsupportedOperationExcetion) {
                    println()
                }
            }
        }
    }
```

- 예외가 출력되어도 애플리케이션이 계속 실행될 수 있는 이유? -> SupervisorJob이 launch 빌더로 시작된 자식 코루틴에 대해 CoroutineExceptionHandler를 호출하기 때문

## 18.3 CoroutineExceptionHandler: 예외 처리를 위한 마지막 수단

- 자식 코루틴은 처리되지 않은 예외를 부모 코루틴에 전파하고, 이 예외가 슈퍼바이저나 루트 코루틴에 도달하면 예외를 전파할 수 없음 -> 처리되지 않은 예외는 CoroutineExceptionHandler로 전파

```kotlin
/**
 * 커스텀 코루틴 예외 핸들러를 가진 컴포넌트
 */
class ComponentWithScope(dispatcher: CoroutineDispatcher = Dispatchers.Default) {
    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        println("[ERROR] ${e.message}")
    }
    private val scope = CoroutineScope {
        SupervisorJob() + dispatcher + exceptionHandler
    }
    fun action() = scope.launch {
        throw UnsupportedOperationException("Ouch!")
    }
}
```

- 중간 코루틴에서 CoroutineExceptionHandler는 결코 사용되지 않는다

### 18.3.1 CoroutineExceptionHandler를 launch와 async에 적용할 때의 차이점

- CoroutineExceptionHandler는 최상위 코루틴이 launch로 생성된 경우에만 호출, async에서는 호출되지 않는다.
- 최상위 코루틴이 async로 시작되면 예외를 처리하는 책임은 await()를 호출하는 Deferred 소비자에게 있다. 따라서 CoroutineExceptionHandler는 예외를 무시할 수 있다.

## 18.4 플로우에서 예외 처리

- 일반적으로 플로우의 일부분에서 예외가 발생하면 collect에서 예외가 던져진다
- collect를 try-catch로 감싸면 되지만 긴 플로우 파이프라인을 구축할 때는 catch 연산자를 사용하는 쪽이 더 편리하다

### 18.4.1 catch 연산자로 업스트림 예외 처리

```kotlin
/**
 * catch 연산자를 써서 예외 발생 시 기본 값 방출하기
 */
fun main() = runBlocking {
        excetpionalFlow
            .catch { cause ->
                println("\nHandled: $cause")
                emit(-1)
            }
            .collect {
                println("$it ")
            }
    }
```

- catch 연산자가 오직 업스트림에 대해서만 작동하며, 플로우 처리 파이프라인의 앞쪽에서 발생한 예외들만 잡아낸다

### 18.4.2 술어가 참일 때 플로우의 수집 재시도: retry 연산자

- retry 연산자는 업스트림의 예외를 잡지만 재시도 할 수 있게 지원

## 18.5 코루틴과 플로우 테스트

- 코루틴을 사용하는 코드를 위한 테스트도 일반적인 테스트와 마찬가지로 작동한다.
- 테스트 메서드에서 코루틴을 사용하려면 runTest 코루틴 빌더를 사용하면 된다.

### 18.5.1 코루틴을 사용하는 테스트를 빠르게 만들기: 가상 시간과 테스트 디스패치

- 모든 테스트를 실시간으로 실행해서 지연을 기다리느라 테스트를 느리게 실행하는 대신, 코틀린 코루틴은 가상 시간을 사용해 테스트 실행을 빠르게 진행할 수 있게 해준다.

```kotlin
/**
 * 가상 시간을 사용해 테스트 실행하기
 */
class PlaygroundTest {
    @Test
    fun testDelay() = runTest {
        val startTime = System.currentTimeMillis()
        delay(20.seconds)
        println(System.currentTimeMillis() = startTime)
    }
}
```

```kotlin
/**
 * delay를 통해 가상 시계 진행하기
 */

@OptIn(ExperimentalCoroutineApi::class)
@Test
fun testDelay() = runTest {
        var x = 0
        launch {
            delay(500.milliseconds)
            x++
        }
        launch {
            delay(1.second)
            x++
        }
        println(currentTime) //0

        delay(600.milliseconds)
        assertEquals(1, x)
        println(currentTime) // 600

        delay(500, milliseconds)
        assertEquals(2, x)
        println(currentTime) // 1100
    }
```

### 18.5.2 터빈으로 플로우 테스트

```kotlin
/**
 * 플로우를 리스트로 수집하기
 */
val myFlow = flow {
        emit(1)
        emit(2)
        emit(3)
    }

@Test
fun doTest() = runTest {
    val results = myFlow.toList()
    assertEquals(3, results.size)
}
```

```kotlin
/**
 * 터빈으로 플로우 테스트하기
 */
@Test
fun doTest() = runTest {
        val results = myFlow.test {
            assertEquals(1, awaitItem())
            assertEquals(2, awaitItem())
            assertEquals(3, awaitItem())
            awaitComplete()
        }
    }
```

- 터빈 라이브러리는 플로우 기반의 코드를 간편하게 테스트하게 해준다. 플로우에서 원소를 수집하고 awaitItem과 같은 함수를 사용해 테스트 중인 플로우의 원소 배출을 확인할 수 있다.