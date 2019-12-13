package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.adt.Either;
import com.jnape.palatable.lambda.adt.coproduct.CoProduct4;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;

import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static com.jnape.palatable.lambda.adt.Either.left;
import static com.jnape.palatable.lambda.adt.Either.right;
import static com.jnape.palatable.lambda.functions.builtin.fn3.Times.times;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.functions.recursion.Trampoline.trampoline;
import static java.util.concurrent.CompletableFuture.completedFuture;

public class NewIO {
    private static abstract class Body<A> implements
        CoProduct4<Body.Pure<A>, Body.Impure<A>, Body.Zipped<?, A>, Body.FlatMapped<?, A>, Body<A>> {

        public abstract Either<Body<A>, A> resume();

        public abstract Either<CompletableFuture<Body<A>>, CompletableFuture<A>> resumeAsync(Executor executor);

        public final A yield() {
            return resume().recover(trampoline(body -> body.resume()
                .match(RecursiveResult::recurse,
                       RecursiveResult::terminate)));
        }

        public final IO<A> toIO() {
            return IO.temporary(() -> {

                return null;
            }, executor -> {
                return null;
            });
        }

        private static <A, B> Body<B> zipped(Body<A> body, Body<Fn1<? super A, ? extends B>> bodyFn) {
            return new Zipped<>(body, bodyFn);
        }

        private static <A, B> Body<B> flatMapped(Body<A> body, Fn1<? super A, ? extends Body<B>> bodyFn) {
            return new FlatMapped<>(body, bodyFn);
        }

        private static <A> Body<A> pure(A a) {
            return new Pure<>(a);
        }

        private static <A> Body<A> impure(Fn0<A> fn) {
            return new Impure<>(fn);
        }

        public static void main(String[] args) {
            System.out.println(zipped(pure(1), pure(x -> x + 1)).resume());

            System.out.println(times(100000, p -> zipped(p, pure(x -> x + 1)), pure(1)).yield());
            System.out.println(times(100000, p -> flatMapped(p, x -> pure(x + 1)), pure(1)).yield());
            Body<Integer> zipped1 = zipped(pure(1), impure(() -> x -> x + 1));
            System.out.println(times(16, p -> {
                Body<Fn1<? super Integer, ? extends Integer>> zipped = zipped(p, pure(x -> y -> x + y));
                return zipped(p, zipped);
            }, zipped1).yield());

            Body<Integer> zipped = zipped(pure(1), pure(x -> x + 1));
            System.out.println(zipped(zipped, pure(x -> x + 1)).resume()
                                   .projectA()
                                   .orElseThrow(NoSuchElementException::new)
                                   .resume());

            System.out.println("starting big one");


            Integer result = times(100000, b -> {
                Body<Integer> yielding_fn = zipped(b, impure(() -> {

                    return x -> x + 1;
                }));
                return flatMapped(yielding_fn, x -> impure(() -> {

                    return x + 1;
                }));
            }, impure(() -> {
                System.out.println("yielding first");
                return 1;
            })).yield();

            System.out.println("result = " + result);


        }

        public static final class Pure<A> extends Body<A> {
            private final A a;

            private Pure(A a) {
                this.a = a;
            }

            @Override
            public Either<Body<A>, A> resume() {
                return right(a);
            }

            @Override
            public Either<CompletableFuture<Body<A>>, CompletableFuture<A>> resumeAsync(Executor executor) {
                return right(completedFuture(a));
            }

            @Override
            public <R> R match(Fn1<? super Pure<A>, ? extends R> aFn,
                               Fn1<? super Impure<A>, ? extends R> bFn,
                               Fn1<? super Zipped<?, A>, ? extends R> cFn,
                               Fn1<? super FlatMapped<?, A>, ? extends R> dFn) {
                return aFn.apply(this);
            }
        }

        public static final class Impure<A> extends Body<A> {
            private final Fn0<A> fn;

            private Impure(Fn0<A> fn) {
                this.fn = fn;
            }

            @Override
            public Either<Body<A>, A> resume() {
                return right(fn.apply());
            }

            @Override
            public Either<CompletableFuture<Body<A>>, CompletableFuture<A>> resumeAsync(Executor executor) {
                return right(CompletableFuture.supplyAsync(fn::apply, executor));
            }

            @Override
            public <R> R match(Fn1<? super Pure<A>, ? extends R> aFn,
                               Fn1<? super Impure<A>, ? extends R> bFn,
                               Fn1<? super Zipped<?, A>, ? extends R> cFn,
                               Fn1<? super FlatMapped<?, A>, ? extends R> dFn) {
                return bFn.apply(this);
            }
        }

        public static final class Zipped<A, B> extends Body<B> {
            private final Body<A>                           source;
            private final Body<Fn1<? super A, ? extends B>> fn;

            private Zipped(Body<A> source, Body<Fn1<? super A, ? extends B>> fn) {
                this.source = source;
                this.fn = fn;
            }

            @Override
            public Either<Body<B>, B> resume() {
                Zipped.Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, B> zippedPhi =
                    new Zipped.Phi<RecursiveResult<Body<B>,
                        Either<Body<B>, B>>, B>() {
                        @Override
                        public <Z> RecursiveResult<Body<B>, Either<Body<B>, B>> eliminate(
                            Body<Z> source,
                            Body<Fn1<? super Z, ? extends B>> zBodyFn) {
                            return source.match(
                                pure -> withValue(zBodyFn, pure.a),
                                impure -> withValue(zBodyFn, impure.fn.apply()),
                                zipped -> {
                                    Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, Z> phi =
                                        new Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, Z>() {
                                            @Override
                                            public <Y> RecursiveResult<Body<B>, Either<Body<B>, B>> eliminate(
                                                Body<Y> ySource,
                                                Body<Fn1<? super Y, ? extends Z>> yBodyFn) {
                                                return recurse(flatMapped(
                                                    ySource, y -> flatMapped(
                                                        yBodyFn, yz -> flatMapped(
                                                            zBodyFn, zb -> pure(zb.apply(yz.apply(y)))))));
                                            }
                                        };
                                    return zipped.eliminate(phi);
                                },
                                flatMapped -> {
                                    FlatMapped.Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, Z> phi =
                                        new FlatMapped.Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, Z>() {
                                            @Override
                                            public <Y> RecursiveResult<Body<B>, Either<Body<B>, B>> eliminate(
                                                Body<Y> source, Fn1<? super Y, ? extends Body<Z>> bodyFn) {
                                                return recurse(flatMapped(source,
                                                                          y -> zipped(bodyFn.apply(y), zBodyFn)));
                                            }
                                        };
                                    return flatMapped.eliminate(phi);
                                });
                        }

                        private <Z> RecursiveResult<Body<B>, Either<Body<B>, B>>
                        withValue(Body<Fn1<? super Z, ? extends B>> bodyFn, Z z) {
                            return bodyFn.match(
                                pureFn -> terminate(right(pureFn.a.apply(z))),
                                impureFn -> terminate(right(impureFn.fn.apply().apply(z))),
                                zippedFn -> recurse(flatMapped(zippedFn, f -> pure(f.apply(z)))),
                                flatMappedFn -> recurse(flatMapped(flatMappedFn, f -> pure(f.apply(z)))));
                        }
                    };

                FlatMapped.Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, B> flatMappedPhi =
                    new FlatMapped.Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, B>() {
                        @Override
                        public <Z> RecursiveResult<Body<B>, Either<Body<B>, B>> eliminate(
                            Body<Z> source,
                            Fn1<? super Z, ? extends Body<B>> bodyFn) {
                            return source.match(
                                pure -> terminate(left(bodyFn.apply(pure.a))),
                                impure -> terminate(left(bodyFn.apply(impure.fn.apply()))),
                                zipped -> {
                                    Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, Z> phi =
                                        new Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, Z>() {
                                            @Override
                                            public <Y> RecursiveResult<Body<B>, Either<Body<B>, B>> eliminate(
                                                Body<Y> source,
                                                Body<Fn1<? super Y, ? extends Z>> yBodyFn) {
                                                return recurse(flatMapped(
                                                    source, y -> flatMapped(
                                                        yBodyFn, yz -> bodyFn.apply(yz.apply(y)))));
                                            }
                                        };
                                    return zipped.eliminate(phi);
                                },
                                flatMapped -> {
                                    FlatMapped.Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, Z> phi =
                                        new FlatMapped.Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, Z>() {
                                            @Override
                                            public <Y> RecursiveResult<Body<B>, Either<Body<B>, B>> eliminate(
                                                Body<Y> source, Fn1<? super Y, ? extends Body<Z>> yBodyFn) {
                                                return recurse(flatMapped(source,
                                                                          y -> flatMapped(yBodyFn.apply(y), bodyFn)));
                                            }
                                        };
                                    return flatMapped.eliminate(phi);
                                }
                            );
                        }
                    };

                return trampoline(
                    body -> body.match(
                        pure -> terminate(pure.resume()),
                        impure -> terminate(impure.resume()),
                        zipped -> zipped.eliminate(zippedPhi),
                        flatMapped -> flatMapped.eliminate(flatMappedPhi)),
                    (Body<B>) this);
            }

            @Override
            public Either<CompletableFuture<Body<B>>, CompletableFuture<B>> resumeAsync(Executor executor) {
                throw new UnsupportedOperationException();
            }

            private <R> R eliminate(Zipped.Phi<R, B> phi) {
                return phi.eliminate(source, fn);
            }

            private interface Phi<R, B> {
                <A> R eliminate(Body<A> source, Body<Fn1<? super A, ? extends B>> bodyFn);
            }

            @Override
            public <R> R match(Fn1<? super Pure<B>, ? extends R> aFn,
                               Fn1<? super Impure<B>, ? extends R> bFn,
                               Fn1<? super Zipped<?, B>, ? extends R> cFn,
                               Fn1<? super FlatMapped<?, B>, ? extends R> dFn) {
                return cFn.apply(this);
            }
        }


        public static final class FlatMapped<A, B> extends Body<B> {
            private final Body<A>                           source;
            private final Fn1<? super A, ? extends Body<B>> fn;

            private FlatMapped(Body<A> source, Fn1<? super A, ? extends Body<B>> fn) {
                this.source = source;
                this.fn = fn;
            }

            @Override
            public Either<Body<B>, B> resume() {
                Zipped.Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, B> zippedPhi =
                    new Zipped.Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, B>() {
                        @Override
                        public <Z> RecursiveResult<Body<B>, Either<Body<B>, B>> eliminate(
                            Body<Z> source,
                            Body<Fn1<? super Z, ? extends B>> bodyFn) {
                            return source.match(
                                pure -> recurse(flatMapped(bodyFn, zb -> pure(zb.apply(pure.a)))),
                                impure -> recurse(flatMapped(bodyFn, zb -> pure(zb.apply(impure.fn.apply())))),
                                zipped -> {
                                    Zipped.Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, Z> phi =
                                        new Zipped.Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, Z>() {
                                            @Override
                                            public <Y> RecursiveResult<Body<B>, Either<Body<B>, B>> eliminate(
                                                Body<Y> source, Body<Fn1<? super Y, ? extends Z>> yBodyFn) {
                                                return recurse(flatMapped(
                                                    source, y -> flatMapped(
                                                        yBodyFn, yz -> flatMapped(
                                                            bodyFn, zb -> pure(zb.apply(yz.apply(y)))))));
                                            }
                                        };
                                    return zipped.eliminate(phi);
                                },
                                flatMapped -> {
                                    Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, Z> phi =
                                        new Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, Z>() {
                                            @Override
                                            public <Y> RecursiveResult<Body<B>, Either<Body<B>, B>> eliminate(
                                                Body<Y> source, Fn1<? super Y, ? extends Body<Z>> yBodyFn) {
                                                return recurse(flatMapped(
                                                    source, y -> flatMapped(
                                                        yBodyFn.apply(y), z -> flatMapped(
                                                            bodyFn, zb -> pure(zb.apply(z))))));
                                            }
                                        };
                                    return flatMapped.eliminate(phi);
                                });
                        }
                    };
                Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, B> flatMappedPhi =
                    new Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, B>() {
                        @Override
                        public <Z> RecursiveResult<Body<B>, Either<Body<B>, B>> eliminate(
                            Body<Z> source,
                            Fn1<? super Z, ? extends Body<B>> bodyFn) {
                            return source.match(
                                pure -> recurse(bodyFn.apply(pure.a)),
                                impure -> recurse(bodyFn.apply(impure.fn.apply())),
                                zipped -> {
                                    Zipped.Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, Z> phi =
                                        new Zipped.Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, Z>() {
                                            @Override
                                            public <Y> RecursiveResult<Body<B>, Either<Body<B>, B>> eliminate(
                                                Body<Y> source, Body<Fn1<? super Y, ? extends Z>> yBodyFn) {
                                                return recurse(flatMapped(
                                                    source, y -> flatMapped(yBodyFn, yz -> bodyFn.apply(yz.apply(y)))));
                                            }
                                        };
                                    return zipped.eliminate(phi);
                                },
                                flatMapped -> {
                                    Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, Z> phi =
                                        new Phi<RecursiveResult<Body<B>, Either<Body<B>, B>>, Z>() {
                                            @Override
                                            public <Y> RecursiveResult<Body<B>, Either<Body<B>, B>> eliminate(
                                                Body<Y> source,
                                                Fn1<? super Y, ? extends Body<Z>> yBodyFn) {
                                                return recurse(flatMapped(
                                                    source, y -> flatMapped(
                                                        yBodyFn.apply(y), bodyFn)));
                                            }
                                        };
                                    return flatMapped.eliminate(phi);
                                }
                            );
                        }
                    };

                return trampoline(
                    body -> body.match(
                        pure -> terminate(pure.resume()),
                        impure -> terminate(impure.resume()),
                        zipped -> zipped.eliminate(zippedPhi),
                        flatMapped -> flatMapped.eliminate(flatMappedPhi)),
                    (Body<B>) this);
            }

            @Override
            public Either<CompletableFuture<Body<B>>, CompletableFuture<B>> resumeAsync(Executor executor) {
                throw new UnsupportedOperationException("not yet implemented");
            }

            @Override
            public <R> R match(Fn1<? super Pure<B>, ? extends R> aFn,
                               Fn1<? super Impure<B>, ? extends R> bFn,
                               Fn1<? super Zipped<?, B>, ? extends R> cFn,
                               Fn1<? super FlatMapped<?, B>, ? extends R> dFn) {
                return dFn.apply(this);
            }

            private <R> R eliminate(FlatMapped.Phi<R, B> phi) {
                return phi.eliminate(source, fn);
            }

            private interface Phi<R, B> {
                <A> R eliminate(Body<A> source, Fn1<? super A, ? extends Body<B>> bodyFn);
            }
        }

    }
}
