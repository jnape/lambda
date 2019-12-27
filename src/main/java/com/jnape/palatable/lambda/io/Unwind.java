package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;

import java.util.Queue;
import java.util.concurrent.*;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.functions.recursion.Trampoline.trampoline;
import static com.jnape.palatable.lambda.io.Body.*;
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


}
