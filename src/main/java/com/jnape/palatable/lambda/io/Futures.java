package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.functions.Fn1;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

final class Futures {
    private Futures() {
    }

    public static <A, B> CompletableFuture<B> thenApply(CompletableFuture<A> futureA,
                                                        Fn1<? super A, ? extends B> fn) {
        return futureA.thenApply(fn.toFunction());
    }

    public static <A, B> CompletableFuture<B> thenCompose(CompletableFuture<A> futureA,
                                                          Fn1<? super A, ? extends CompletableFuture<B>> futureFn,
                                                          Executor executor) {
        return futureA.thenComposeAsync(futureFn.toFunction(), executor);
    }
}
