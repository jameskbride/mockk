package io.mockk

import kotlin.reflect.KClass

/**
 * Mediates mocking implementation
 */
interface MockKGateway {
    val mockFactory: MockFactory
    val stubber: Stubber
    val verifier: Verifier
    val callRecorder: CallRecorder
    val instantiator: Instantiator

    fun verifier(ordering: Ordering): CallVerifier

    fun stubFor(mock: Any): Stub

    companion object {
        lateinit var implementation: () -> MockKGateway

        fun registerInstanceFactory(factory: InstanceFactory): Deregisterable {
            implementation().instantiator.registerFactory(factory)
            return object : Deregisterable {
                override fun unregister() {
                    implementation().instantiator.unregisterFactory(factory)
                }
            }
        }

        fun registerInstanceFactory(filterClass: KClass<*>,
                                    factory: () -> Any): Deregisterable {
            return registerInstanceFactory(object : InstanceFactory {
                override fun instantiate(cls: KClass<*>): Any? {
                    if (filterClass == cls) {
                        return factory()
                    }
                    return null
                }
            })
        }
    }

    fun <T> runCoroutine(block: suspend () -> T): T


    /**
     * Create new mocks or spies
     */
    interface MockFactory {
        fun <T : Any> mockk(cls: KClass<T>,
                            name: String?,
                            moreInterfaces: Array<out KClass<*>>): T

        fun <T : Any> spyk(cls: KClass<T>?,
                           objToCopy: T?,
                           name: String?,
                           moreInterfaces: Array<out KClass<*>>): T

        fun clear(mocks: Array<out Any>,
                  answers: Boolean,
                  recordedCalls: Boolean,
                  childMocks: Boolean)

        fun staticMockk(cls: KClass<*>)
        fun staticUnMockk(cls: KClass<*>)
    }

    interface Stub {
        val name: String

        val type: KClass<*>

        fun addAnswer(matcher: InvocationMatcher, answer: Answer<*>)

        fun answer(invocation: Invocation): Any?

        fun childMockK(call: Call): Any?

        fun recordCall(invocation: Invocation)

        fun allRecordedCalls(): List<Invocation>

        fun clear(answers: Boolean, calls: Boolean, childMocks: Boolean)

        fun handleInvocation(self: Any,
                             method: MethodDescription,
                             originalCall: () -> Any?,
                             args: Array<out Any?>): Any?

        fun toStr(): String
    }


    /**
     * Stub calls
     */
    interface Stubber {
        fun <T> every(mockBlock: (MockKMatcherScope.() -> T)?,
                      coMockBlock: (suspend MockKMatcherScope.() -> T)?): MockKStubScope<T>
    }

    /**
     * Verify calls
     */
    interface Verifier {
        fun verify(ordering: Ordering,
                   inverse: Boolean,
                   atLeast: Int,
                   atMost: Int,
                   exactly: Int,
                   mockBlock: (MockKVerificationScope.() -> Unit)?,
                   coMockBlock: (suspend MockKVerificationScope.() -> Unit)?)

        fun checkWasNotCalled(mocks: List<Any>)
    }

    /**
     * Builds a list of calls
     */
    interface CallRecorder {
        val calls: List<Call>

        fun startStubbing()

        fun startVerification()

        fun catchArgs(round: Int, n: Int = 64)

        fun <T : Any> matcher(matcher: Matcher<*>, cls: KClass<T>): T

        fun call(invocation: Invocation): Any?

        fun answer(answer: Answer<*>)

        fun doneVerification()

        fun hintNextReturnType(cls: KClass<*>, n: Int)

        fun cancel()

        fun estimateCallRounds(): Int

        fun nCalls(): Int
    }

    /**
     * Verifier takes the list of calls and checks what invocations happened to the mocks
     */
    interface CallVerifier {
        fun verify(calls: List<Call>, min: Int, max: Int): VerificationResult
    }

    /**
     * Result of verification
     */
    data class VerificationResult(val matches: Boolean, val message: String? = null)

    /**
     * Instantiates empty object for provided class
     */
    interface Instantiator {
        fun <T : Any> instantiate(cls: KClass<T>): T

        fun anyValue(cls: KClass<*>, orInstantiateVia: () -> Any? = { instantiate(cls) }): Any?

        fun <T : Any> proxy(cls: KClass<T>,
                            useDefaultConstructor: Boolean,
                            instantiateOnFailure: Boolean,
                            moreInterfaces: Array<out KClass<*>>, stub: Stub): Any

        fun <T : Any> signatureValue(cls: KClass<T>): T

        fun isPassedByValue(cls: KClass<*>): Boolean

        fun deepEquals(obj1: Any?, obj2: Any?): Boolean

        fun registerFactory(factory: InstanceFactory)

        fun unregisterFactory(factory: InstanceFactory)

        fun staticMockk(cls: KClass<*>, stub: Stub)

        fun staticUnMockk(cls: KClass<*>)
    }

    /**
     * Factory of dummy objects
     */
    interface InstanceFactory {
        fun instantiate(cls: KClass<*>): Any?
    }

    /**
     * Allows to unregister something was registered before
     */
    interface Deregisterable {
        fun unregister()
    }


}

inline fun <T : MockKGateway.Deregisterable, R> T.use(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        try {
            this.unregister()
        } catch (closeException: Throwable) {
            // skip
        }
    }
}

