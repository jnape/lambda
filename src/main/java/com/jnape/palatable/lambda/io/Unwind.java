package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.adt.Either;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.functions.builtin.fn1.Id.id;
import static com.jnape.palatable.lambda.functions.builtin.fn3.Times.times;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.functions.recursion.Trampoline.trampoline;
import static com.jnape.palatable.lambda.io.Body.*;
import static com.jnape.palatable.lambda.io.IO.io;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.ForkJoinPool.commonPool;

public class Unwind {

    static <A> Body<A> leftMost(Body<A> unoptimizedBodyA) {
        return trampoline(
            bodyA -> bodyA.match(
                RecursiveResult::terminate,
                RecursiveResult::terminate,
                zippedA -> zippedA.eliminate(new Zipped.Phi<RecursiveResult<Body<A>, Body<A>>, A>() {
                    @Override
                    public <Z> RecursiveResult<Body<A>, Body<A>> eliminate(
                        Body<Z> bodyZ, Body<Fn1<? super Z, ? extends A>> bodyZA) {
                        return bodyZ.match(
                            pureZ -> terminate(zipped(pureZ, bodyZA)),
                            impureZ -> terminate(zipped(impureZ, bodyZA)),
                            zippedZ -> Correct.zippedWithZip(zippedZ, bodyZA),
                            flatMappedZ -> flatMappedZ.eliminate(
                                new FlatMapped.Phi<RecursiveResult<Body<A>, Body<A>>, Z>() {
                                    @Override
                                    public <Y> RecursiveResult<Body<A>, Body<A>> eliminate(
                                        Body<Y> bodyY, Fn1<? super Y, ? extends Body<Z>> yBodyZ) {
                                        return recurse(flatMapped(bodyY, y -> zipped(yBodyZ.apply(y), bodyZA)));
                                    }
                                }));
                    }
                }),
                flatMappedA -> flatMappedA.eliminate(new FlatMapped.Phi<RecursiveResult<Body<A>, Body<A>>, A>() {
                    @Override
                    public <Z> RecursiveResult<Body<A>, Body<A>> eliminate(
                        Body<Z> bodyZ, Fn1<? super Z, ? extends Body<A>> zBodyA) {
                        return bodyZ.match(
                            pureZ -> terminate(flatMapped(pureZ, zBodyA)),
                            impureZ -> terminate(flatMapped(impureZ, zBodyA)),
                            zippedZ -> zippedZ.eliminate(new Zipped.Phi<RecursiveResult<Body<A>, Body<A>>, Z>() {
                                @Override
                                public <Y> RecursiveResult<Body<A>, Body<A>> eliminate(
                                    Body<Y> bodyY, Body<Fn1<? super Y, ? extends Z>> bodyYZ) {
                                    return recurse(flatMapped(bodyYZ, yz -> flatMapped(bodyY, zBodyA.contraMap(yz))));
                                }
                            }),
                            flatMappedZ -> Correct.flatMappedWithFlatMap(flatMappedZ, zBodyA));
                    }
                })
            ),
            unoptimizedBodyA);
    }

    private static final Queue<CompletableFuture<?>> futures = new LinkedBlockingQueue<>(100_000);

    public static <A, B> CompletableFuture<B> flatMap(CompletableFuture<A> futureA,
                                                      Fn1<? super A, ? extends CompletableFuture<B>> fn,
                                                      Executor ex) {
        CompletableFuture<B> ref = new CompletableFuture<>();
        futures.add(ref);

        futureA.thenCompose(fn.toFunction())
            .whenComplete((res, t) -> {
                if (t == null) {
                    ref.complete(res);
                } else {
                    ref.completeExceptionally(t);
                }
            });
        return ref;
    }

    public static CompletableFuture<Integer> manyTimesWithFlatMap(int x, Executor ex) {
        CompletableFuture<Integer> source = supplyAsync(() -> {
            if (x % 1_000 == 0)
                System.out.println(Thread.currentThread() + " : " + x);

            return x;
        });
        Fn1<Integer, CompletableFuture<Integer>> fn = y -> y > 50000
            ? completedFuture(y)
            : manyTimesWithFlatMap(y + 1, ex);
        return flatMap(source, fn, ex);
    }


    public static void main(String[] args) throws InterruptedException {

        CountDownLatch oneShot = new CountDownLatch(1);
        ForkJoinPool   ex      = commonPool();
        flatMap(manyTimesWithFlatMap(1, ex), __ -> supplyAsync(() -> {
            oneShot.countDown();
            return UNIT;
        }), ex).join();
//        if (!oneShot.await(1000, TimeUnit.MILLISECONDS))
//            System.out.println("stack overflow");
//        else
            System.out.println("got here");

    }

}
