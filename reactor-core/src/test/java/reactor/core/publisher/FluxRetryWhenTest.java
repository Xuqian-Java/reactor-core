/*
 * Copyright (c) 2011-2017 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.core.publisher;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.Scannable;
import reactor.test.StepVerifier;
import reactor.test.subscriber.AssertSubscriber;
import reactor.util.context.Context;

import static org.assertj.core.api.Assertions.assertThat;

public class FluxRetryWhenTest {

	Flux<Integer> justError = Flux.concat(Flux.just(1),
			Flux.error(new RuntimeException("forced failure 0")));

	Flux<Integer> rangeError = Flux.concat(Flux.range(1, 2),
			Flux.error(new RuntimeException("forced failure 0")));

	@Test(expected = NullPointerException.class)
	public void sourceNull() {
		new FluxRetryWhen<>(null, v -> v);
	}

	@Test(expected = NullPointerException.class)
	public void whenFactoryNull() {
		Flux.never()
		    .retryWhen(null);
	}

	@Test
	public void cancelsOther() {
		AtomicBoolean cancelled = new AtomicBoolean();
		Flux<Integer> when = Flux.range(1, 10)
		                         .doOnCancel(() -> cancelled.set(true));

		StepVerifier.create(justError.retryWhen(other -> when))
		            .thenCancel()
		            .verify();

		assertThat(cancelled.get()).isTrue();
	}

	@Test
	public void cancelTwiceCancelsOtherOnce() {
		AtomicInteger cancelled = new AtomicInteger();
		Flux<Integer> when = Flux.range(1, 10)
		                         .doOnCancel(cancelled::incrementAndGet);

		justError.retryWhen(other -> when)
		         .subscribe(new BaseSubscriber<Integer>() {
			         @Override
			         protected void hookOnSubscribe(Subscription subscription) {
				         subscription.request(1);
				         subscription.cancel();
				         subscription.cancel();
			         }
		         });

		assertThat(cancelled.get()).isEqualTo(1);
	}

	@Test
	public void directOtherErrorPreventsSubscribe() {
		AtomicBoolean sourceSubscribed = new AtomicBoolean();
		AtomicBoolean sourceCancelled = new AtomicBoolean();
		Flux<Integer> source = justError
		                           .doOnSubscribe(sub -> sourceSubscribed.set(true))
		                           .doOnCancel(() -> sourceCancelled.set(true));

		Flux<Integer> retry = source.retryWhen(other -> Mono.error(new IllegalStateException("boom")));

		StepVerifier.create(retry)
		            .expectSubscription()
		            .verifyErrorMessage("boom");

		assertThat(sourceSubscribed.get()).isFalse();
		assertThat(sourceCancelled.get()).isFalse();
	}

	@Test
	public void lateOtherErrorCancelsSource() {
		AtomicBoolean sourceSubscribed = new AtomicBoolean();
		AtomicBoolean sourceCancelled = new AtomicBoolean();
		AtomicInteger count = new AtomicInteger();
		Flux<Integer> source = justError
		                           .doOnSubscribe(sub -> sourceSubscribed.set(true))
		                           .doOnCancel(() -> sourceCancelled.set(true));


		Flux<Integer> retry = source.retryWhen(other -> other.flatMap(l ->
				count.getAndIncrement() == 0 ? Mono.just(l) : Mono.<Long>error(new IllegalStateException("boom"))));

		StepVerifier.create(retry)
		            .expectSubscription()
		            .expectNext(1)
		            .expectNext(1)
		            .verifyErrorMessage("boom");

		assertThat(sourceSubscribed.get()).isTrue();
		assertThat(sourceCancelled.get()).isTrue();
	}

	@Test
	public void directOtherEmptyPreventsSubscribeAndCompletes() {
		AtomicBoolean sourceSubscribed = new AtomicBoolean();
		AtomicBoolean sourceCancelled = new AtomicBoolean();
		Flux<Integer> source = justError
		                           .doOnSubscribe(sub -> sourceSubscribed.set(true))
		                           .doOnCancel(() -> sourceCancelled.set(true));

		Flux<Integer> retry = source.retryWhen(other -> Flux.empty());

		StepVerifier.create(retry)
		            .expectSubscription()
		            .verifyComplete();

		assertThat(sourceSubscribed.get()).isFalse();
		assertThat(sourceCancelled.get()).isFalse();
	}

	@Test
	public void lateOtherEmptyCancelsSourceAndCompletes() {
		AtomicBoolean sourceSubscribed = new AtomicBoolean();
		AtomicBoolean sourceCancelled = new AtomicBoolean();
		Flux<Integer> source = justError
		                           .doOnSubscribe(sub -> sourceSubscribed.set(true))
		                           .doOnCancel(() -> sourceCancelled.set(true));

		Flux<Integer> retry = source.retryWhen(other -> other.take(1));

		StepVerifier.create(retry)
		            .expectSubscription()
		            .expectNext(1) //original
		            .expectNext(1) //retry
		            .verifyComplete(); //retry terminated

		assertThat(sourceSubscribed.get()).isTrue();
		assertThat(sourceCancelled.get()).isTrue();
	}

	@Test
	public void coldRepeater() {
		AssertSubscriber<Integer> ts = AssertSubscriber.create();

		justError.retryWhen(v -> Flux.range(1, 10))
		         .subscribe(ts);

		ts.assertValues(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
		  .assertComplete()
		  .assertNoError();
	}

	@Test
	public void coldRepeaterBackpressured() {
		AssertSubscriber<Integer> ts = AssertSubscriber.create(0);

		rangeError.retryWhen(v -> Flux.range(1, 5))
		          .subscribe(ts);

		ts.assertNoValues()
		  .assertNoError()
		  .assertNotComplete();

		ts.request(1);

		ts.assertValues(1)
		  .assertNoError()
		  .assertNotComplete();

		ts.request(2);

		ts.assertValues(1, 2, 1)
		  .assertNoError()
		  .assertNotComplete();

		ts.request(5);

		ts.assertValues(1, 2, 1, 2, 1, 2, 1, 2)
		  .assertNoError()
		  .assertNotComplete();

		ts.request(10);

		ts.assertValues(1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2)
		  .assertComplete()
		  .assertNoError();
	}

	@Test
	public void coldEmpty() {
		AssertSubscriber<Integer> ts = AssertSubscriber.create(0);

		rangeError.retryWhen(v -> Flux.empty())
		          .subscribe(ts);

		ts.assertNoValues()
		  .assertNoError()
		  .assertComplete();
	}

	@Test
	public void coldError() {
		AssertSubscriber<Integer> ts = AssertSubscriber.create(0);

		rangeError.retryWhen(v -> Flux.error(new RuntimeException("forced failure")))
		          .subscribe(ts);

		ts.assertNoValues()
		  .assertError(RuntimeException.class)
		  .assertErrorMessage("forced failure")
		  .assertNotComplete();
	}

	@Test
	public void whenFactoryThrows() {
		AssertSubscriber<Integer> ts = AssertSubscriber.create();

		rangeError.retryWhen(v -> {
			throw new RuntimeException("forced failure");
		})
		          .subscribe(ts);

		ts.assertNoValues()
		  .assertError(RuntimeException.class)
		  .assertErrorMessage("forced failure")
		  .assertNotComplete();

	}

	@Test
	public void whenFactoryReturnsNull() {
		AssertSubscriber<Integer> ts = AssertSubscriber.create();

		rangeError.retryWhen(v -> null)
		          .subscribe(ts);

		ts.assertNoValues()
		  .assertError(NullPointerException.class)
		  .assertNotComplete();

	}

	@Test
	public void retryErrorsInResponse() {
		AssertSubscriber<Integer> ts = AssertSubscriber.create();

		rangeError.retryWhen(v -> v.map(a -> {
			throw new RuntimeException("forced failure");
		}))
		          .subscribe(ts);

		ts.assertValues(1, 2)
		  .assertError(RuntimeException.class)
		  .assertErrorMessage("forced failure")
		  .assertNotComplete();

	}

	@Test
	public void retryAlways() {
		AssertSubscriber<Integer> ts = AssertSubscriber.create(0);

		rangeError.retryWhen(v -> v)
		          .subscribe(ts);

		ts.request(8);

		ts.assertValues(1, 2, 1, 2, 1, 2, 1, 2)
		  .assertNoError()
		  .assertNotComplete();
	}

	Flux<String> exponentialRetryScenario() {
		AtomicInteger i = new AtomicInteger();
		return Flux.<String>create(s -> {
			if (i.incrementAndGet() == 4) {
				s.next("hey");
			}
			else {
				s.error(new RuntimeException("test " + i));
			}
		}).retryWhen(repeat -> repeat.zipWith(Flux.range(1, 3), (t1, t2) -> t2)
		                             .flatMap(time -> Mono.delay(Duration.ofSeconds(time))));
	}

	@Test
	public void exponentialRetry() {
		StepVerifier.withVirtualTime(this::exponentialRetryScenario)
		            .thenAwait(Duration.ofSeconds(6))
		            .expectNext("hey")
		            .expectComplete()
		            .verify();
	}

	@Test
    public void scanMainSubscriber() {
        Subscriber<Integer> actual = new LambdaSubscriber<>(null, e -> {}, null, null);
        FluxRetryWhen.RetryWhenMainSubscriber<Integer> test =
        		new FluxRetryWhen.RetryWhenMainSubscriber<>(actual, null, Flux.empty());
        Subscription parent = Operators.emptySubscription();
        test.onSubscribe(parent);

        Assertions.assertThat(test.scan(Scannable.ScannableAttr.PARENT)).isSameAs(parent);
        Assertions.assertThat(test.scan(Scannable.ScannableAttr.ACTUAL)).isSameAs(actual);
        test.requested = 35;
        Assertions.assertThat(test.scan(Scannable.LongAttr.REQUESTED_FROM_DOWNSTREAM)).isEqualTo(35L);

        Assertions.assertThat(test.scan(Scannable.BooleanAttr.CANCELLED)).isFalse();
        test.cancel();
        Assertions.assertThat(test.scan(Scannable.BooleanAttr.CANCELLED)).isTrue();
    }

	@Test
    public void scanOtherSubscriber() {
		Subscriber<Integer> actual = new LambdaSubscriber<>(null, e -> {}, null, null);
        FluxRetryWhen.RetryWhenMainSubscriber<Integer> main =
        		new FluxRetryWhen.RetryWhenMainSubscriber<>(actual, null, Flux.empty());
        FluxRetryWhen.RetryWhenOtherSubscriber test = new FluxRetryWhen.RetryWhenOtherSubscriber();
        test.main = main;

        Assertions.assertThat(test.scan(Scannable.ScannableAttr.PARENT)).isSameAs(main.otherArbiter);
        Assertions.assertThat(test.scan(Scannable.ScannableAttr.ACTUAL)).isSameAs(main);
    }


	@Test
	public void inners() {
		Subscriber<Integer> actual = new LambdaSubscriber<>(null, e -> {}, null, null);
		Subscriber<Throwable> signaller = new LambdaSubscriber<>(null, e -> {}, null, null);
		Flux<Integer> when = Flux.empty();
		FluxRetryWhen.RetryWhenMainSubscriber<Integer> main = new FluxRetryWhen
				.RetryWhenMainSubscriber<>(actual, signaller, when);

		List<Scannable> inners = main.inners().collect(Collectors.toList());

		assertThat(inners).containsExactly((Scannable) signaller, main.otherArbiter);
	}
}
