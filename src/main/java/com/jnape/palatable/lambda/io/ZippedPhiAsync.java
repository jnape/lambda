package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.adt.Either;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;
import com.jnape.palatable.lambda.io.NewIO.Body;
import com.jnape.palatable.lambda.io.NewIO.Body.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static com.jnape.palatable.lambda.adt.Either.left;
import static com.jnape.palatable.lambda.adt.Either.right;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.io.Futures.thenApply;
import static com.jnape.palatable.lambda.io.Futures.thenCompose;
import static com.jnape.palatable.lambda.io.NewIO.Body.*;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;

// todo: these futures could technically be further deferred, but I think I've hit the point of diminishing returns
final class ZippedPhiAsync<A> implements
    Zipped.Phi<RecursiveResult<Body<A>, Either<CompletableFuture<Body<A>>, CompletableFuture<A>>>, A> {
    private final Executor executor;

    ZippedPhiAsync(Executor executor) {
        this.executor = executor;
    }

    @Override
    public <Z> RecursiveResult<Body<A>, Either<CompletableFuture<Body<A>>, CompletableFuture<A>>> eliminate(
        Body<Z> bodyZ, Body<Fn1<? super Z, ? extends A>> bodyZA) {
        return bodyZ.match(
            pureZ -> zipWithFuture(bodyZA, completedFuture(pureZ.value)),
            impureZ -> zipWithFuture(bodyZA, supplyAsync(impureZ.computation.toSupplier(), executor)),
            zippedZ -> zippedZ.eliminate(new Zipped.Phi<
                RecursiveResult<Body<A>, Either<CompletableFuture<Body<A>>, CompletableFuture<A>>>, Z>() {
                @Override
                public <Y> RecursiveResult<Body<A>, Either<CompletableFuture<Body<A>>, CompletableFuture<A>>> eliminate(
                    Body<Y> bodyY,
                    Body<Fn1<? super Y, ? extends Z>> bodyYZ) {
                    return recurse(zipped(bodyY, zipped(bodyYZ, zipped(bodyZA,
                                                                       pure(za -> yz -> y -> za.apply(yz.apply(y)))))));

                }
            }),
            flatMappedZ -> flatMappedZ.eliminate(new FlatMapped.Phi<
                RecursiveResult<Body<A>, Either<CompletableFuture<Body<A>>, CompletableFuture<A>>>, Z>() {
                @Override
                public <Y> RecursiveResult<Body<A>, Either<CompletableFuture<Body<A>>, CompletableFuture<A>>> eliminate(
                    Body<Y> bodyY, Fn1<? super Y, ? extends Body<Z>> yBodyZ) {
                    return bodyY.match(
                        pureY -> terminate(left(thenApply(completedFuture(pureY.value),
                                                          y -> zipped(yBodyZ.apply(y), bodyZA)))),
                        impureY -> terminate(left(thenApply(
                            supplyAsync(impureY.computation.toSupplier(), executor),
                            y -> zipped(yBodyZ.apply(y), bodyZA)))),
                        zippedY -> recurse(zipped(flatMapped(zippedY, yBodyZ), bodyZA)),
                        flatMappedY -> recurse(zipped(flatMapped(flatMappedY, yBodyZ), bodyZA))
                    );
                }
            })
        );
    }

    private <Z> RecursiveResult<Body<A>, Either<CompletableFuture<Body<A>>, CompletableFuture<A>>>
    zipWithFuture(Body<Fn1<? super Z, ? extends A>> bodyZA, CompletableFuture<Z> futureZ) {
        return bodyZA.match(
            pureZA -> terminate(right(thenApply(futureZ, pureZA.value))),
            impureZA -> terminate(right(thenCompose(futureZ,
                                                    supplyAsync(impureZA.computation.toSupplier(), executor)))),
            zippedZA -> zippedZA.eliminate(new Zipped.Phi<
                RecursiveResult<Body<A>,
                    Either<CompletableFuture<Body<A>>, CompletableFuture<A>>>, Fn1<? super Z, ? extends A>>() {
                @Override
                public <Y> RecursiveResult<Body<A>, Either<CompletableFuture<Body<A>>, CompletableFuture<A>>> eliminate(
                    Body<Y> bodyY,
                    Body<Fn1<? super Y, ? extends Fn1<? super Z, ? extends A>>> bodyYZA) {


//                    CompletableFuture<Y> yCompletableFuture = bodyY.unsafeRunAsync(executor);
//                    CompletableFuture<Fn1<? super Y, ? extends Fn1<? super Z, ? extends A>>> fn1CompletableFuture =
//                        bodyYZA.unsafeRunAsync(executor);


//                    CompletableFuture<Fn1<? super Z, ? extends A>> futureZA =
//                        yCompletableFuture.thenCombineAsync(fn1CompletableFuture, (y, yza) -> yza.apply(y), executor);


                    Body<Z> impure = impure(futureZ::get);

                    return recurse(zipped(bodyY, zipped(impure, zipped(bodyYZA, pure(yza -> {
                        return z -> {
                            return y -> yza.apply(y).apply(z);
                        };
                    })))));


//                    return terminate(right(futureZA.thenCombine(futureZ, (za, z) -> za.apply(z))));


//                    return terminate(left(thenApply(futureZ, z -> zipped(
//                        bodyY, flatMapped(bodyYZA, yza -> pure(y -> yza.apply(y).apply(z)))))));
                }
            }),
            flatMappedZA -> flatMappedZA.eliminate(new FlatMapped.Phi<
                RecursiveResult<Body<A>,
                    Either<CompletableFuture<Body<A>>, CompletableFuture<A>>>, Fn1<? super Z, ? extends A>>() {
                @Override
                public <Y> RecursiveResult<Body<A>, Either<CompletableFuture<Body<A>>, CompletableFuture<A>>> eliminate(
                    Body<Y> bodyY,
                    Fn1<? super Y, ? extends Body<Fn1<? super Z, ? extends A>>> yBodyZA) {
                    return terminate(left(thenApply(futureZ, z -> flatMapped(
                        bodyY, y -> flatMapped(yBodyZA.apply(y), za -> pure(za.apply(z)))))));
                }
            })
        );
    }

}
