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

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.util.context.Context;

/**
 * Concatenates a fixed array of Publishers' values.
 *
 * @param <T> the value type
 *
 * @see <a href="https://github.com/reactor/reactive-streams-commons">Reactive-Streams-Commons</a>
 */
final class FluxConcatIterable<T> extends Flux<T> {

	final Iterable<? extends Publisher<? extends T>> iterable;

	FluxConcatIterable(Iterable<? extends Publisher<? extends T>> iterable) {
		this.iterable = Objects.requireNonNull(iterable, "iterable");
	}

	@Override
	public void subscribe(Subscriber<? super T> s, Context ctx) {

		Iterator<? extends Publisher<? extends T>> it;

		try {
			it = Objects.requireNonNull(iterable.iterator(),
					"The Iterator returned is null");
		}
		catch (Throwable e) {
			Operators.error(s, Operators.onOperatorError(e));
			return;
		}

		ConcatIterableSubscriber<T> parent = new ConcatIterableSubscriber<>(s, it, ctx);

		s.onSubscribe(parent);

		if (!parent.isCancelled()) {
			parent.onComplete();
		}
	}

	static final class ConcatIterableSubscriber<T>
			extends Operators.MultiSubscriptionSubscriber<T, T> {

		final Iterator<? extends Publisher<? extends T>> it;

		volatile int wip;
		@SuppressWarnings("rawtypes")
		static final AtomicIntegerFieldUpdater<ConcatIterableSubscriber> WIP =
				AtomicIntegerFieldUpdater.newUpdater(ConcatIterableSubscriber.class,
						"wip");

		long produced;

		ConcatIterableSubscriber(Subscriber<? super T> actual,
				Iterator<? extends Publisher<? extends T>> it, Context ctx) {
			super(actual, ctx);
			this.it = it;
		}

		@Override
		public void onNext(T t) {
			produced++;

			actual.onNext(t);
		}

		@Override
		public void onComplete() {
			if (WIP.getAndIncrement(this) == 0) {
				Iterator<? extends Publisher<? extends T>> a = this.it;
				do {
					if (isCancelled()) {
						return;
					}

					boolean b;

					try {
						b = a.hasNext();
					}
					catch (Throwable e) {
						onError(Operators.onOperatorError(this, e));
						return;
					}

					if (isCancelled()) {
						return;
					}

					if (!b) {
						actual.onComplete();
						return;
					}

					Publisher<? extends T> p;

					try {
						p = Objects.requireNonNull(it.next(),
								"The Publisher returned by the iterator is null");
					}
					catch (Throwable e) {
						actual.onError(Operators.onOperatorError(this, e));
						return;
					}

					if (isCancelled()) {
						return;
					}

					long c = produced;
					if (c != 0L) {
						produced = 0L;
						produced(c);
					}

					p.subscribe(this);

					if (isCancelled()) {
						return;
					}

				}
				while (WIP.decrementAndGet(this) != 0);
			}

		}
	}
}