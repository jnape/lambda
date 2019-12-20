package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;

import java.util.concurrent.Executor;

import static com.jnape.palatable.lambda.adt.Either.right;
import static com.jnape.palatable.lambda.functions.builtin.fn2.Into.into;
import static com.jnape.palatable.lambda.functions.builtin.fn2.Tupler2.tupler;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.io.Body.flatMapped;
import static com.jnape.palatable.lambda.io.Body.pure;

class AsyncPhis {

    static <A> Progress.Phi<A, RecursiveResult<Progress<?, A>, Future<A>>> unwrapProgressPhi(Executor executor) {
        return new Progress.Phi<A, RecursiveResult<Progress<?, A>, Future<A>>>() {
            @Override
            public <Z> RecursiveResult<Progress<?, A>, Future<A>> eliminate(
                Future<Z> futureZ, Body<Fn1<? super Z, ? extends A>> bodyZA) {
                return bodyZA.resumeAsync(executor).match(
                    progressZA -> {
                        return recurse(progressZA.eliminate(
                            new Progress.Phi<Fn1<? super Z, ? extends A>, Progress<?, A>>() {
                                @Override
                                public <Y> Progress<?, A> eliminate(
                                    Future<Y> futureY,
                                    Body<Fn1<? super Y, ? extends Fn1<? super Z, ? extends A>>> bodyYZA) {
                                    return new Progress<>(
                                        futureZ.zip(futureY.fmap(tupler())),
                                        right(into((y, z) -> flatMapped(bodyYZA, yza -> pure(yza.apply(y).apply(z))))));
                                }
                            },
                            new Progress.Psi<Fn1<? super Z, ? extends A>, Progress<?, A>>() {
                                @Override
                                public <Y> Progress<?, A> eliminate(
                                    Future<Y> futureY,
                                    Fn1<? super Y, ? extends Body<Fn1<? super Z, ? extends A>>> yBodyZA) {
                                    return new Progress<>(
                                        futureZ.zip(futureY.fmap(tupler())),
                                        right(into((y, z) -> flatMapped(yBodyZA.apply(y), za -> pure(za.apply(z)))))
                                    );
                                }
                            }));
                    },
                    futureZA -> terminate(futureZ.zip(futureZA)));
            }
        };
    }

    public static <A> Progress.Psi<A, RecursiveResult<Progress<?, A>, Future<A>>> unwrapProgressPsi(Executor executor) {
        return new Progress.Psi<A, RecursiveResult<Progress<?, A>, Future<A>>>() {
            @Override
            public <Z> RecursiveResult<Progress<?, A>, Future<A>> eliminate(
                Future<Z> futureZ, Fn1<? super Z, ? extends Body<A>> zBodyA) {
                return terminate(futureZ.fmap(zBodyA)
                                     .flatMap(body -> new Future<>(body.unsafeRunAsync(executor))));
            }
        };
    }
}
