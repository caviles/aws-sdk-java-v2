/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.utils.internal;

import java.util.function.Function;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.annotations.SdkInternalApi;

/**
 * Maps a subscriber of one type to another type. If an exception is thrown by the mapping function itself, the error
 * will be propagated to the downstream subscriber as if it had come from the publisher and then the subscription will
 * be implicitly cancelled and no further events from the publisher will be passed along.
 */
@SdkInternalApi
public class MappingSubscriber<T, U> implements Subscriber<T> {

    private final Subscriber<? super U> subscriber;
    private final Function<T, U> mapFunction;
    private boolean isCancelled = false;

    private MappingSubscriber(Subscriber<? super U> subscriber,
                              Function<T, U> mapFunction) {
        this.subscriber = subscriber;
        this.mapFunction = mapFunction;
    }

    public static <T, U> MappingSubscriber<T, U> create(Subscriber<? super U> subscriber,
                                                        Function<T, U> mapFunction) {
        return new MappingSubscriber<>(subscriber, mapFunction);
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        subscriber.onSubscribe(subscription);
    }

    @Override
    public void onError(Throwable throwable) {
        if (!isCancelled) {
            subscriber.onError(throwable);
        }
    }

    @Override
    public void onComplete() {
        if (!isCancelled) {
            subscriber.onComplete();
        }
    }

    @Override
    public void onNext(T t) {
        if (!isCancelled) {
            try {
                subscriber.onNext(mapFunction.apply(t));
            } catch (RuntimeException e) {
                // If the map function throws an exception, the subscription must be implicitly cancelled. The publisher
                // will not know this is happening, so it needs to be handled by this class.
                this.isCancelled = true;
                subscriber.onError(e);
            }
        }
    }
}
