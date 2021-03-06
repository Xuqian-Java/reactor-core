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

import org.reactivestreams.Subscriber;
import reactor.util.context.Context;

/**
 * @author Stephane Maldini
 */
final class MonoMaterialize<T> extends MonoOperator<T, Signal<T>> {

	MonoMaterialize(Mono<T> source) {
		super(source);
	}

	@Override
	public void subscribe(Subscriber<? super Signal<T>> subscriber, Context ctx) {
		source.subscribe(new FluxMaterialize.MaterializeSubscriber<>(new MonoNext.NextSubscriber<Signal<T>>(subscriber)),
				ctx);
	}
}
