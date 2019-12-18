package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.adt.Either;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;
import com.jnape.palatable.lambda.io.Body.*;

import static com.jnape.palatable.lambda.adt.Either.right;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.io.Body.*;

final class ZippedPhiSync<A> implements
    Zipped.Phi<RecursiveResult<Body<A>, Either<Body<A>, A>>, A> {

    @Override
    public <Z> RecursiveResult<Body<A>, Either<Body<A>, A>> eliminate(
        Body<Z> source,
        Body<Fn1<? super Z, ? extends A>> zBodyFn) {
        return source.match(
            pure -> withValue(zBodyFn, pure.value),
            impure -> withValue(zBodyFn, impure.computation.apply()),
            zipped -> zipped.eliminate(new Zipped.Phi<RecursiveResult<Body<A>, Either<Body<A>, A>>, Z>() {
                @Override
                public <Y> RecursiveResult<Body<A>, Either<Body<A>, A>> eliminate(
                    Body<Y> bodyY,
                    Body<Fn1<? super Y, ? extends Z>> bodyYZ) {
                    return recurse(flatMapped(bodyY,
                                              y -> flatMapped(bodyYZ,
                                                              yz -> flatMapped(zBodyFn,
                                                                               zb -> pure(zb.apply(yz.apply(y)))))));
                }
            }),
            flatMapped -> flatMapped.eliminate(new FlatMapped.Phi<RecursiveResult<Body<A>, Either<Body<A>, A>>, Z>() {
                @Override
                public <Y> RecursiveResult<Body<A>, Either<Body<A>, A>> eliminate(
                    Body<Y> bodyY, Fn1<? super Y, ? extends Body<Z>> yBodyZ) {
                    return recurse(flatMapped(bodyY, y -> zipped(yBodyZ.apply(y), zBodyFn)));
                }
            }));
    }

    private <Z> RecursiveResult<Body<A>, Either<Body<A>, A>> withValue(Body<Fn1<? super Z, ? extends A>> bodyZA, Z z) {
        return bodyZA.match(
            pureFn -> terminate(right(pureFn.value.apply(z))),
            impureFn -> terminate(right(impureFn.computation.apply().apply(z))),
            zippedFn -> recurse(flatMapped(zippedFn, f -> pure(f.apply(z)))),
            flatMappedFn -> recurse(flatMapped(flatMappedFn, f -> pure(f.apply(z)))));
    }
}
