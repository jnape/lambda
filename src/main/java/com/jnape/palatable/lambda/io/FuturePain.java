package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.functions.Fn1;

import java.util.concurrent.*;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;

public class FuturePain {
    public static <A, B> CompletableFuture<B> runAfterFutureIsDone(
        CompletableFuture<A> futureA,
        Fn1<? super A, ? extends CompletableFuture<B>> fn,
        Executor es) {

        CompletableFuture<B> ref = new CompletableFuture<>();


        futureA
            .whenCompleteAsync((a, t) -> {
                if (t == null) {
                    fn.apply(a).whenCompleteAsync((b, t2) -> {
                        if (t2 == null) {
                            ref.complete(b);
                        } else {
                            ref.completeExceptionally(t2);
                        }
                    }, es);
                } else {
                    ref.completeExceptionally(t);
                }
            }, es);


        return ref;
    }

    public static CompletableFuture<Integer> manyTimesWithFlatMap(int x, Executor es) {
        return runAfterFutureIsDone(CompletableFuture.supplyAsync(() -> {
                                        if (x % 100_000 == 0)
                                            System.out.println(x);
                                        return x;
                                    }, es),
                                    y -> y == 10_000_000
                                        ? completedFuture(y)
                                        : manyTimesWithFlatMap(y + 1, es),
                                    es);
    }

    public static void main(String[] args) throws Exception {
        System.in.read();

        ExecutorService singleThread = Executors.newSingleThreadExecutor();


        CountDownLatch  oneShot      = new CountDownLatch(1);
        Object res = runAfterFutureIsDone(manyTimesWithFlatMap(1, singleThread), __ -> supplyAsync(() -> {
            oneShot.countDown();
            return null;
        }, singleThread), singleThread).join();
        System.out.println("done");
        singleThread.shutdown();

    }
}
