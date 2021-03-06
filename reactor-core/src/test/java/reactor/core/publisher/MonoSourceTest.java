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

import org.junit.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.Fuseable;
import reactor.core.Scannable;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

public class MonoSourceTest {

	@Test
	public void empty() {
		Mono<Integer> m = Mono.from(Flux.empty());
		assertTrue(m == Mono.<Integer>empty());
		StepVerifier.create(m)
		            .verifyComplete();
	}

	@Test
	public void just() {
		Mono<Integer> m = Mono.from(Flux.just(1));
		assertTrue(m instanceof MonoJust);
		StepVerifier.create(m)
	                .expectNext(1)
	                .verifyComplete();
	}

	@Test
	public void justNext() {
		StepVerifier.create(Mono.from(Flux.just(1, 2, 3)))
	                .expectNext(1)
	                .verifyComplete();
	}

	@Test
	public void asJustNext() {
		StepVerifier.create(Flux.just(1, 2, 3).as(Mono::from))
	                .expectNext(1)
	                .verifyComplete();
	}

	@Test
	public void monoNext() {
		StepVerifier.create(Flux.just(1, 2, 3).next())
	                .expectNext(1)
	                .verifyComplete();
	}

	@Test
	public void monoDirect() {
		StepVerifier.create(Flux.just(1).as(Mono::fromDirect))
	                .expectNext(1)
	                .verifyComplete();
	}

	@Test
	public void monoDirectHidden() {
		StepVerifier.create(Flux.just(1).hide().as(Mono::fromDirect))
	                .expectNext(1)
	                .verifyComplete();
	}

	@Test
	public void monoDirectIdentity() {
		StepVerifier.create(Mono.just(1).as(Mono::fromDirect))
	                .expectNext(1)
	                .verifyComplete();
	}

	@Test
	public void monoDirectPlainFuseable() {
		StepVerifier.create(Mono.just(1).as(TestPubFuseable::new))
	                .expectNext(1)
	                .verifyComplete();
	}

	@Test
	public void monoDirectPlain() {
		StepVerifier.create(Mono.just(1).as(TestPub::new))
	                .expectNext(1)
	                .verifyComplete();
	}

	final static class TestPubFuseable implements Publisher<Integer>, Fuseable {
		final Mono<Integer> m;

		TestPubFuseable(Mono<Integer> mono) {
			this.m = mono;
		}

		@Override
		public void subscribe(Subscriber<? super Integer> s) {
			m.subscribe(s);
		}
	}

	final static class TestPub implements Publisher<Integer>, Fuseable {
		final Mono<Integer> m;

		TestPub(Mono<Integer> mono) {
			this.m = mono;
		}

		@Override
		public void subscribe(Subscriber<? super Integer> s) {
			m.subscribe(s);
		}
	}

	@Test
	public void transform() {
		StepVerifier.create(Mono.just(1).transform(m -> Flux.just(1, 2, 3)))
	                .expectNext(1)
	                .verifyComplete();
	}

	@Test
	public void onAssemblyDescription() {
		String monoOnAssemblyStr = Mono.just(1).checkpoint("onAssemblyDescription").toString();
		System.out.println(Mono.just(1).checkpoint("onAssemblyDescription"));
		assertTrue("Description not included: " + monoOnAssemblyStr, monoOnAssemblyStr.contains("\"description\" : \"onAssemblyDescription\""));
	}

	@Test
	public void scanSubscriber() {
		Flux<String> source = Flux.just("foo");
		Mono<String> test = Mono.fromDirect(source);

		assertThat(Scannable.from(test).scan(Scannable.ScannableAttr.PARENT)).isSameAs(source);
		assertThat(Scannable.from(test).scan(Scannable.ScannableAttr.ACTUAL)).isNull();
	}


	@Test
	public void scanSubscriberHide() {
		Flux<String> source = Flux.just("foo").hide();
		Mono<String> test = Mono.fromDirect(source);

		assertThat(Scannable.from(test).scan(Scannable.ScannableAttr.PARENT)).isSameAs(source);
		assertThat(Scannable.from(test).scan(Scannable.ScannableAttr.ACTUAL)).isNull();
	}

	@Test
	public void scanSubscriberIgnore() {
		Flux<String> source = Flux.just("foo");
		MonoIgnorePublisher<String> test = new MonoIgnorePublisher<>(source);

		assertThat(test.scan(Scannable.ScannableAttr.PARENT)).isSameAs(source);
		assertThat(test.scan(Scannable.ScannableAttr.ACTUAL)).isNull();
	}

	@Test
	public void scanSubscriberFrom() {
		Flux<String> source = Flux.just("foo");
		MonoFromPublisher<String> test = new MonoFromPublisher<>(source);

		assertThat(test.scan(Scannable.ScannableAttr.PARENT)).isSameAs(source);
		assertThat(test.scan(Scannable.ScannableAttr.ACTUAL)).isNull();
	}
}