package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.adt.Either;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;

import java.io.IOException;
import java.util.concurrent.ForkJoinPool;

import static com.jnape.palatable.lambda.functions.builtin.fn3.Times.times;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.functions.recursion.Trampoline.trampoline;
import static com.jnape.palatable.lambda.io.Body.*;
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

    public static void main(String[] args) {

        ForkJoinPool executor = commonPool();

        Body<Integer>                                 bodyX = impure(() -> 1);
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
