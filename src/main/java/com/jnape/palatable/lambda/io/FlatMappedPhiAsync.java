package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.adt.Either;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;
import com.jnape.palatable.lambda.io.NewIO.Body;
import com.jnape.palatable.lambda.io.NewIO.Body.FlatMapped;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static com.jnape.palatable.lambda.adt.Either.left;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.io.Futures.thenApply;
import static com.jnape.palatable.lambda.io.NewIO.Body.flatMapped;
import static java.util.concurrent.CompletableFuture.supplyAsync;

class FlatMappedPhiAsync<A> implements
    FlatMapped.Phi<RecursiveResult<Body<A>, Either<CompletableFuture<Body<A>>, CompletableFuture<A>>>, A> {
    private final Executor executor;

    FlatMappedPhiAsync(Executor executor) {
        this.executor = executor;
    }

    @Override
    public <Z> RecursiveResult<Body<A>, Either<CompletableFuture<Body<A>>, CompletableFuture<A>>> eliminate(
        Body<Z> bodyZ, Fn1<? super Z, ? extends Body<A>> zBodyA) {
        return bodyZ.match(
            pureZ -> recurse(zBodyA.apply(pureZ.value)),
            impureZ -> terminate(left(thenApply(supplyAsync(impureZ.computation.toSupplier(), executor), zBodyA))),
            zippedZ -> zippedZ.eliminate(new ZippedPhiAsync<>(executor)).biMap(
                bodyZ_ -> flatMapped(bodyZ_, zBodyA),
                futureBodyZOrFutureZ -> futureBodyZOrFutureZ.match(
                    futureBodyZ -> left(thenApply(futureBodyZ, bodyZ_ -> flatMapped(bodyZ_, zBodyA))),
                    futureZ -> left(thenApply(futureZ, zBodyA)))),
            flatMappedZ -> flatMappedZ.eliminate(new FlatMapped.Phi<
                RecursiveResult<Body<A>,
                    Either<CompletableFuture<Body<A>>, CompletableFuture<A>>>, Z>() {
                @Override
                public <Y> RecursiveResult<Body<A>, Either<CompletableFuture<Body<A>>, CompletableFuture<A>>> eliminate(
                    Body<Y> bodyY, Fn1<? super Y, ? extends Body<Z>> yBodyZ) {
                    return recurse(flatMapped(bodyY, y -> flatMapped(yBodyZ.apply(y), zBodyA)));
                }
            }));
    }
}
