package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.adt.Either;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

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

    public static <A, B> CompletableFuture<B> flatMap(CompletableFuture<A> futureA,
                                                      Fn1<? super A, ? extends CompletableFuture<B>> fn,
                                                      CompletableFuture<B> ref) {
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

    public static CompletableFuture<Integer> manyTimesWithFlatMap(int x) {
        return flatMap(supplyAsync(() -> {
            if (x % 1_000_000 == 0)
                System.out.println(Thread.currentThread() + " : " + x);

            return x + 1;
        }), y -> {
            if (y > 100_000_000)
                return completedFuture(y);

            return manyTimesWithFlatMap(y + 1);
        }, new CompletableFuture<>());
    }

    public static CompletableFuture<Integer> manyTimesWithFlatMap(int x, CompletableFuture<Integer> ref) {
        return flatMap(supplyAsync(() -> {
            if (x % 1_000_000 == 0)
                System.out.println(Thread.currentThread() + " : " + x);

            return x + 1;
        }), y -> {
            if (y > 100_000_000)
                return completedFuture(y);

            return manyTimesWithFlatMap(y + 1, ref);
        }, new CompletableFuture<>());
    }

    public static CompletableFuture<Integer> manyTimes(int x) {
        return manyTimes(x, new CompletableFuture<>());
    }

    public static CompletableFuture<Integer> manyTimes(int x, CompletableFuture<Integer> nextRef) {
        supplyAsync(() -> {
            if (x % 1_000_000 == 0)
                System.out.println(Thread.currentThread() + " : " + x);

            if (x > 100_000_000) {
                nextRef.complete(x);
            } else {
                manyTimes(x + 1, nextRef);
            }
            return null;
        });
        return nextRef;
    }

    public static IO<Integer> manyTimesIO(int x) {
        return io(() -> {
            if (x > 10_000_000)
                return io(x);

            return manyTimesIO(x + 1);
        }).flatMap(id());
    }

    public static void main(String[] args) throws IOException {
        System.in.read();
        manyTimesWithFlatMap(1).join();
//        manyTimesIO(1).unsafePerformIO();
    }

    public static void main2(String[] args) {

        ForkJoinPool executor = commonPool();

        Body<Integer> bodyX = impure(() -> 1);
        Body<Fn1<? super Integer, ? extends Integer>> bodyF = impure(() -> {
//            System.out.println("f");
//            Thread.sleep(1000);
            return x -> x + 1;
        });

        Body<Integer> bodyInt = times(10000, b -> zipped(b, bodyF), bodyX);

//        leftMost(bodyInt)

        Either<Progress<?, Integer>, Future<Integer>> resumed = bodyInt.resumeAsync(executor);


        Integer res = bodyInt.unsafeRunAsync(executor).join();


        System.out.println(res);

    }
}
