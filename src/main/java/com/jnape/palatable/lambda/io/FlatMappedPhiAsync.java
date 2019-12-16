package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.adt.Either;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;
import com.jnape.palatable.lambda.io.NewIO.Body;
import com.jnape.palatable.lambda.io.NewIO.Body.FlatMapped;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jnape.palatable.lambda.adt.Either.right;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.io.Futures.thenCompose;
import static java.util.concurrent.CompletableFuture.supplyAsync;

class FlatMappedPhiAsync<A> implements
    FlatMapped.Phi<RecursiveResult<Body<A>, Either<Either<Body<CompletableFuture<A>>, Body.Parallel<?, A>>,
        CompletableFuture<A>>>, A> {
    private final Executor executor;

    static AtomicInteger counter = new AtomicInteger(0);

    FlatMappedPhiAsync(Executor executor) {
        this.executor = executor;
    }

    @Override
    public <Z> RecursiveResult<Body<A>, Either<Either<Body<CompletableFuture<A>>, Body.Parallel<?, A>>,
        CompletableFuture<A>>> eliminate(
        Body<Z> bodyZ, Fn1<? super Z, ? extends Body<A>> zBodyA) {
        return bodyZ.match(
            pureZ -> AsyncPhis.pureWithFlatMap(pureZ, zBodyA),
            impureZ -> {
                //todo: this is still dangerous
                CompletableFuture<Z> futureZ = supplyAsync(impureZ.computation.toSupplier(), executor);

                return terminate(right(thenCompose(futureZ, z -> zBodyA.apply(z).unsafeRunAsync(executor), executor)));

                // Future z -> (z -> Body a) -> ???
                // Future z -> Body (z -> a) -> ???
            },
            zippedZ -> AsyncPhis.zippedWithFlatMap(zippedZ, zBodyA),
            flatMappedZ -> AsyncPhis.flatMappedWithFlatMap(flatMappedZ, zBodyA)
        );
    }
}
