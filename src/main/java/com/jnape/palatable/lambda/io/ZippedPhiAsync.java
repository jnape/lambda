package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.adt.Either;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;
import com.jnape.palatable.lambda.io.NewIO.Body;
import com.jnape.palatable.lambda.io.NewIO.Body.Zipped;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static com.jnape.palatable.lambda.adt.Either.left;
import static com.jnape.palatable.lambda.adt.Either.right;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static java.util.concurrent.CompletableFuture.supplyAsync;

final class ZippedPhiAsync<A> implements
    Zipped.Phi<RecursiveResult<Body<A>, Either<Either<Body<CompletableFuture<A>>, Body.Parallel<?, A>>, CompletableFuture<A>>>, A> {
    private final Executor executor;

    ZippedPhiAsync(Executor executor) {
        this.executor = executor;
    }

    @Override
    public <Z> RecursiveResult<Body<A>, Either<Either<Body<CompletableFuture<A>>, Body.Parallel<?, A>>,
        CompletableFuture<A>>> eliminate(Body<Z> bodyZ, Body<Fn1<? super Z, ? extends A>> bodyZA) {
        return bodyZ.match(
            pureZ -> AsyncPhis.pureWithZip(pureZ, bodyZA),
            impureZ -> {
                CompletableFuture<Z> futureZ = supplyAsync(impureZ.computation.toSupplier(), executor);
                return terminate(left(right(new Body.Parallel<>(futureZ, bodyZA))));
            },
            zippedZ -> AsyncPhis.zippedWithZip(zippedZ, bodyZA),
            flatMappedZ -> AsyncPhis.flatMappedWithZip(flatMappedZ, bodyZA)
        );
    }

}
