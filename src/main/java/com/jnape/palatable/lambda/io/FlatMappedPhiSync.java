package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.adt.Either;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;
import com.jnape.palatable.lambda.io.Body.FlatMapped;
import com.jnape.palatable.lambda.io.Body.FlatMapped.Phi;
import com.jnape.palatable.lambda.io.Body.Zipped;

import static com.jnape.palatable.lambda.adt.Either.left;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.io.Body.flatMapped;

final class FlatMappedPhiSync<A> implements Phi<RecursiveResult<Body<A>,
    Either<Body<A>, A>>, A> {
    @Override
    public <Z> RecursiveResult<Body<A>, Either<Body<A>, A>> eliminate(Body<Z> bodyZ,
                                                                      Fn1<? super Z, ? extends Body<A>> zBodyA) {
        return bodyZ.match(
            pure -> terminate(left(zBodyA.apply(pure.value))),
            impure -> terminate(left(zBodyA.apply(impure.computation.apply()))),
            zipped -> zipped.eliminate(new Zipped.Phi<RecursiveResult<Body<A>, Either<Body<A>, A>>, Z>() {
                @Override
                public <Y> RecursiveResult<Body<A>, Either<Body<A>, A>> eliminate(
                    Body<Y> bodyY,
                    Body<Fn1<? super Y, ? extends Z>> bodyYZ) {
                    return recurse(flatMapped(bodyY, y -> flatMapped(bodyYZ, yz -> zBodyA.apply(yz.apply(y)))));
                }
            }),
            flatMapped -> flatMapped.eliminate(new FlatMapped.Phi<RecursiveResult<Body<A>, Either<Body<A>, A>>, Z>() {
                @Override
                public <Y> RecursiveResult<Body<A>, Either<Body<A>, A>> eliminate(
                    Body<Y> bodyY, Fn1<? super Y, ? extends Body<Z>> yBodyZ) {
                    return recurse(flatMapped(bodyY, y -> flatMapped(yBodyZ.apply(y), zBodyA)));
                }
            })
        );
    }
}
