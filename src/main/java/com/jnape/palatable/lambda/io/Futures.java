package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.functions.Fn1;

import java.util.concurrent.CompletableFuture;

final class Futures {
    private Futures() {
    }

    public static <A, B> CompletableFuture<B> thenApply(CompletableFuture<A> futureA,
                                                        Fn1<? super A, ? extends B> fn) {
        return futureA.thenApply(fn.toFunction());
    }

    public static <A, B> CompletableFuture<B> thenCompose(CompletableFuture<A> futureA,
                                                          CompletableFuture<Fn1<? super A, ? extends B>> futureFn) {
        return futureA.thenCompose(a -> futureFn.thenApply(f -> f.apply(a)));
    }

    public static <A, B> CompletableFuture<B> thenCompose(CompletableFuture<A> futureA,
                                                          Fn1<? super A, ? extends CompletableFuture<B>> futureFn) {
        return futureA.thenCompose(futureFn.toFunction());
    }
}
