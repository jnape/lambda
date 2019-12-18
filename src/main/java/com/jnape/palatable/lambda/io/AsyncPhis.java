package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.adt.hlist.Tuple2;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;

import java.util.concurrent.Executor;

import static com.jnape.palatable.lambda.adt.Either.left;
import static com.jnape.palatable.lambda.adt.Either.right;
import static com.jnape.palatable.lambda.functions.builtin.fn1.Id.id;
import static com.jnape.palatable.lambda.functions.builtin.fn2.Into.into;
import static com.jnape.palatable.lambda.functions.builtin.fn2.Tupler2.tupler;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.functions.recursion.Trampoline.trampoline;
import static com.jnape.palatable.lambda.io.Body.*;

class AsyncPhis {

    static <Z, A, B> RecursiveResult<Body<A>, B> zippedWithZip(
        Body.Zipped<?, Z> zippedZ,
        Body<Fn1<? super Z, ? extends A>> bodyZA) {
        return zippedZ.eliminate(new Zipped.Phi<RecursiveResult<Body<A>, B>, Z>() {
            @Override
            public <Y> RecursiveResult<Body<A>, B> eliminate(Body<Y> bodyY,
                                                             Body<Fn1<? super Y, ? extends Z>> bodyYZ) {
                return recurse(zipped(bodyY, zipped(bodyYZ, flatMapped(
                    bodyZA, za -> pure(yz -> y -> za.apply(yz.apply(y)))))));
            }
        });

    }

    static <Z, A, B> RecursiveResult<Body<A>, B> flatMappedWithZip(
        FlatMapped<?, Z> flatMappedZ,
        Body<Fn1<? super Z, ? extends A>> bodyZA) {
        return flatMappedZ.eliminate(new FlatMapped.Phi<RecursiveResult<Body<A>, B>, Z>() {
            @Override
            public <Y> RecursiveResult<Body<A>, B> eliminate(
                Body<Y> bodyY, Fn1<? super Y, ? extends Body<Z>> yBodyZ) {


                return recurse(flatMapped(zipped(
                    bodyZA, zipped(
                        bodyY, pure(y -> za -> flatMapped(
                            yBodyZ.apply(y), z -> pure(za.apply(z)))))), id()));
            }
        });

    }

    static <Z, A> RecursiveResult<Body<A>, RecursiveResult<Progress<?, A>, Future<A>>> zippedWithFlatMap(
        Body.Zipped<?, Z> zippedZ,
        Fn1<? super Z, ? extends Body<A>> zBodyA) {
        return terminate(zippedZ.resumeAsync(null)
                             .match(progressZ -> progressZ.eliminate(
                                 new Progress.Phi<Z, RecursiveResult<Progress<?, A>, Future<A>>>() {
                                     @Override
                                     public <Y> RecursiveResult<Progress<?, A>, Future<A>> eliminate(
                                         Future<Y> futureY, Body<Fn1<? super Y, ? extends Z>> bodyYZ) {
                                         throw new UnsupportedOperationException("not yet implemented");
                                     }
                                 },
                                 null),
                                    futureZ -> recurse(new Progress<>(futureZ, right(zBodyA)))));

        return zippedZ.eliminate(new Zipped.Phi<RecursiveResult<Body<A>, RecursiveResult<Progress<?, A>, Future<A>>>,
            Z>() {
            @Override
            public <Y> RecursiveResult<Body<A>, RecursiveResult<Progress<?, A>, Future<A>>> eliminate(
                Body<Y> bodyY, Body<Fn1<? super Y, ? extends Z>> bodyYZ) {


                //fixme: recurse with zipped
//                return recurse(flatMapped(zipped(bodyY, flatMapped(bodyYZ, yz -> pure(y -> zBodyA.apply(yz.apply(y)
//                )))), id()));
//                return recurse(flatMapped(flatMapped(bodyY, y -> flatMapped(bodyYZ, yz -> pure(yz.apply(y)))),
//                zBodyA));
            }
        });
    }

    // Correct
    static <Z, A, B> RecursiveResult<Body<A>, B> pureWithZip(Pure<Z> pureZ, Body<Fn1<? super Z, ? extends A>> bodyZA) {
        return recurse(flatMapped(bodyZA, za -> pure(za.apply(pureZ.value))));
    }

    // Correct
    static <Z, A, B> RecursiveResult<Body<A>, B> pureWithFlatMap(Pure<Z> pureZ,
                                                                 Fn1<? super Z, ? extends Body<A>> zBodyA) {
        return recurse(zBodyA.apply(pureZ.value));
    }

    // Correct
    static <Z, A, B> RecursiveResult<Body<A>, B> flatMappedWithFlatMap(FlatMapped<?, Z> flatMappedZ,
                                                                       Fn1<? super Z, ? extends Body<A>> zBodyA) {
        return flatMappedZ.eliminate(new FlatMapped.Phi<RecursiveResult<Body<A>, B>, Z>() {
            @Override
            public <Y> RecursiveResult<Body<A>, B> eliminate(Body<Y> bodyY, Fn1<? super Y, ? extends Body<Z>> yBodyZ) {
                return recurse(flatMapped(bodyY, y -> flatMapped(yBodyZ.apply(y), zBodyA)));
            }
        });
    }

    static <A> RecursiveResult<Progress<?, A>, Future<A>> unwind(Body<A> bodyA, Executor executor) {
        return trampoline(
            bodyA_ -> {
                return bodyA_.match(
                    pureA -> terminate(terminate(Future.completed(pureA.value))),
                    impureA -> terminate(terminate(Future.start(impureA.computation, executor))),
                    zippedA -> zippedA.eliminate(new Zipped.Phi<RecursiveResult<Body<A>, RecursiveResult<Progress<?, A>,
                        Future<A>>>, A>() {
                        @Override
                        public <Z> RecursiveResult<Body<A>, RecursiveResult<Progress<?, A>, Future<A>>> eliminate(
                            Body<Z> bodyZ, Body<Fn1<? super Z, ? extends A>> bodyZA) {
                            return bodyZ.match(
                                pureZ -> AsyncPhis.pureWithZip(pureZ, bodyZA),
                                impureZ -> terminate(recurse(new Progress<>(Future.start(impureZ.computation, executor),
                                                                            left(bodyZA)))),
                                zippedZ -> AsyncPhis.zippedWithZip(zippedZ, bodyZA),
                                flatMappedZ -> AsyncPhis.flatMappedWithZip(flatMappedZ, bodyZA)
                            );
                        }
                    }),
                    flatMappedA -> {
                        FlatMapped.Phi<RecursiveResult<Body<A>, RecursiveResult<Progress<?, A>, Future<A>>>, A> phi1 =
                            new FlatMapped.Phi<RecursiveResult<Body<A>, RecursiveResult<Progress<?, A>, Future<A>>>,
                                A>() {
                                @Override
                                public <Z> RecursiveResult<Body<A>, RecursiveResult<Progress<?, A>, Future<A>>> eliminate(
                                    Body<Z> bodyZ, Fn1<? super Z, ? extends Body<A>> zBodyA) {
                                    return bodyZ.match(
                                        pureZ -> AsyncPhis.pureWithFlatMap(pureZ, zBodyA),
                                        impureZ -> terminate(recurse(new Progress<>(Future.start(impureZ.computation,
                                                                                                 executor),
                                                                                    right(zBodyA)))),
                                        zippedZ -> AsyncPhis.zippedWithFlatMap(zippedZ, zBodyA),
                                        flatMappedZ -> AsyncPhis.flatMappedWithFlatMap(flatMappedZ, zBodyA)
                                    );
                                }
                            };
                        return flatMappedA.eliminate(phi1);
                    }
                );
            },
            bodyA);
    }

    static <A> Progress.Phi<A, RecursiveResult<Progress<?, A>, Future<A>>> unwrapProgressPhi(Executor executor) {
        return new Progress.Phi<A, RecursiveResult<Progress<?, A>, Future<A>>>() {
            @Override
            public <Z> RecursiveResult<Progress<?, A>, Future<A>> eliminate(
                Future<Z> futureZ, Body<Fn1<? super Z, ? extends A>> bodyZA) {
                return bodyZA.resumeAsync(executor).match(
                    progressZA -> recurse(progressZA.eliminate(new Progress.Phi<Fn1<? super Z, ? extends A>,
                        Progress<?, A>>() {
                        @Override
                        public <Y> Progress<?, A> eliminate(
                            Future<Y> futureY,
                            Body<Fn1<? super Y, ? extends Fn1<? super Z, ? extends A>>> bodyYZA) {
                            Future<Tuple2<Y, Z>> futureYZ = futureZ.zip(futureY.fmap(tupler()));
                            return new Progress<>(
                                futureYZ, right(into((y, z) -> flatMapped(
                                bodyYZA, yza -> pure(yza.apply(y).apply(z))))));
                        }
                    }, new Progress.Psi<Fn1<? super Z, ? extends A>, Progress<?, A>>() {
                        @Override
                        public <Y> Progress<?, A> eliminate(
                            Future<Y> futureY,
                            Fn1<? super Y, ? extends Body<Fn1<? super Z, ? extends A>>> yBodyZA) {
                            Future<Tuple2<Y, Z>> futureYZ = futureZ.zip(futureY.fmap(tupler()));
                            return new Progress<>(
                                futureYZ,
                                right(into((y, z) -> flatMapped(yBodyZA.apply(y), za -> pure(za.apply(z))))));
                        }
                    })),
                    futureZA -> terminate(futureZ.zip(futureZA)));
            }
        };
    }

    static <A> Progress.Psi<A, RecursiveResult<Progress<?, A>, Future<A>>> unwrapProgressPsi(Executor executor) {
        return new Progress.Psi<A, RecursiveResult<Progress<?, A>, Future<A>>>() {
            @Override
            public <Z> RecursiveResult<Progress<?, A>, Future<A>> eliminate(
                Future<Z> futureZ, Fn1<? super Z, ? extends Body<A>> zBodyA) {
                return terminate(futureZ.flatMap(z -> new Future<>(zBodyA.apply(z).unsafeRunAsync(executor))));
            }
        };
    }
}
