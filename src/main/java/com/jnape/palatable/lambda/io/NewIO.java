package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.adt.Either;
import com.jnape.palatable.lambda.adt.coproduct.CoProduct4;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import static com.jnape.palatable.lambda.adt.Either.left;
import static com.jnape.palatable.lambda.adt.Either.right;
import static com.jnape.palatable.lambda.functions.builtin.fn1.Id.id;
import static com.jnape.palatable.lambda.functions.builtin.fn3.Times.times;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.functions.recursion.Trampoline.trampoline;
import static java.lang.Thread.sleep;

class NewIO {
    static abstract class Body<A> implements
        CoProduct4<Body.Pure<A>, Body.Impure<A>, Body.Zipped<?, A>, Body.FlatMapped<?, A>, Body<A>> {

        final Either<Body<A>, A> resume() {
            return trampoline(
                body -> body.match(
                    pure -> terminate(right(pure.value)),
                    impure -> terminate(right(impure.computation.apply())),
                    zipped -> zipped.eliminate(new ZippedPhiSync<>()),
                    flatMapped -> flatMapped.eliminate(new FlatMappedPhiSync<>())),
                this);
        }

        static final class Progress<A, B> {
            private final Future<A>                                                                    futureA;
            private final Either<Body<Fn1<? super A, ? extends B>>, Fn1<? super A, ? extends Body<B>>> composition;

            Progress(Future<A> futureA,
                     Either<Body<Fn1<? super A, ? extends B>>, Fn1<? super A, ? extends Body<B>>> composition) {
                this.futureA = futureA;
                this.composition = composition;
            }

            <R> R eliminate(Phi<B, R> phi, Psi<B, R> psi) {
                return composition.match(bodyAB -> phi.eliminate(futureA, bodyAB),
                                         aBodyB -> psi.eliminate(futureA, aBodyB));
            }

            interface Phi<B, R> {
                <A> R eliminate(Future<A> futureA, Body<Fn1<? super A, ? extends B>> bodyAB);
            }

            interface Psi<B, R> {
                <A> R eliminate(Future<A> futureA, Fn1<? super A, ? extends Body<B>> aBodyB);
            }
        }

        abstract RecursiveResult<Progress<?, A>, Future<A>> resumeAsync(Executor executor);

        final A unsafeRunSync() {
            return resume().recover(trampoline(body -> body.resume()
                .match(RecursiveResult::recurse,
                       RecursiveResult::terminate)));
        }

        final CompletableFuture<A> unsafeRunAsync(Executor executor) {

            resumeAsync(executor)
                .<Future<A>>match(trampoline(progress -> {
                    return progress.eliminate(null, null);
                }), id())
                .unsafeRun();
        }

        static <A> Body<A> pure(A a) {
            return new Pure<>(a);
        }

        static <A> Body<A> impure(Fn0<A> fn) {
            return new Impure<>(fn);
        }

        static <A, B> Body<B> zipped(Body<A> body, Body<Fn1<? super A, ? extends B>> bodyFn) {
            return new Zipped<>(body, bodyFn);
        }

        static <A, B> Body<B> flatMapped(Body<A> body, Fn1<? super A, ? extends Body<B>> bodyFn) {
            return new FlatMapped<>(body, bodyFn);
        }

        public static void main(String[] args) throws IOException {
//            System.out.println(zipped(pure(1), pure(x -> x + 1)).resume());
//
//            System.out.println(times(100000, p -> zipped(p, pure(x -> x + 1)), pure(1)).unsafeRunSync());
//            System.out.println(times(100000, p -> flatMapped(p, x -> pure(x + 1)), pure(1)).unsafeRunSync());
//            Body<Integer> zipped1 = zipped(pure(1), impure(() -> x -> x + 1));
//            System.out.println(times(16, p -> {
//                Body<Fn1<? super Integer, ? extends Integer>> zipped = zipped(p, pure(x -> y -> x + y));
//                return zipped(p, zipped);
//            }, zipped1).unsafeRunSync());
//
//            Body<Integer> zipped = zipped(pure(1), pure(x -> x + 1));
//            System.out.println(zipped(zipped, pure(x -> x + 1)).resume()
//                                   .projectA()
//                                   .orElseThrow(NoSuchElementException::new)
//                                   .resume());
//
//            System.out.println("starting big one");
//
//
//            Integer result = times(100000, b -> {
//                Body<Integer> yielding_fn = zipped(b, impure(() -> {
//
//                    return x -> x + 1;
//                }));
//                return flatMapped(yielding_fn, x -> impure(() -> {
//
//                    return x + 1;
//                }));
//            }, impure(() -> {
//                System.out.println("yielding first");
//                return 1;
//            })).unsafeRunSync();
//
//            System.out.println("result = " + result);

            System.in.read();

            new Thread(() -> {
                while (true) {
                    try {
                        sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }) {{
                start();
            }};

            Integer res = times(10_000, bi -> zipped(bi, impure(() -> {
//                System.out.println(Thread.currentThread());
//                Thread.sleep(500);
                return x -> x + 1;
            })), impure(() -> {
//                System.out.println(Thread.currentThread());
//                Thread.sleep(1000);
                return 1;
            }))
                .unsafeRunAsync(ForkJoinPool.commonPool()).join();


            System.out.println("res = " + res);
        }

        public static final class Pure<A> extends Body<A> {
            public final A value;

            private Pure(A value) {
                this.value = value;
            }

            @Override
            public <R> R match(Fn1<? super Pure<A>, ? extends R> aFn,
                               Fn1<? super Impure<A>, ? extends R> bFn,
                               Fn1<? super Zipped<?, A>, ? extends R> cFn,
                               Fn1<? super FlatMapped<?, A>, ? extends R> dFn) {
                return aFn.apply(this);
            }

            @Override
            RecursiveResult<Progress<?, A>, Future<A>> resumeAsync(Executor executor) {
                return terminate(Future.completed(value));
            }
        }

        public static final class Impure<A> extends Body<A> {
            public final Fn0<A> computation;

            private Impure(Fn0<A> computation) {
                this.computation = computation;
            }

            @Override
            public <R> R match(Fn1<? super Pure<A>, ? extends R> aFn,
                               Fn1<? super Impure<A>, ? extends R> bFn,
                               Fn1<? super Zipped<?, A>, ? extends R> cFn,
                               Fn1<? super FlatMapped<?, A>, ? extends R> dFn) {
                return bFn.apply(this);
            }

            @Override
            RecursiveResult<Progress<?, A>, Future<A>> resumeAsync(Executor executor) {
                return terminate(Future.start(computation, executor));
            }
        }


        public static final class Zipped<A, B> extends Body<B> {
            public final Body<A>                           source;
            public final Body<Fn1<? super A, ? extends B>> fn;

            private Zipped(Body<A> source, Body<Fn1<? super A, ? extends B>> fn) {
                this.source = source;
                this.fn = fn;
            }

            public <R> R eliminate(Zipped.Phi<R, B> phi) {
                return phi.eliminate(source, fn);
            }

            public interface Phi<R, B> {
                <A> R eliminate(Body<A> source, Body<Fn1<? super A, ? extends B>> bodyFn);
            }

            public interface Psi<R> {
                <A> R eliminate(Impure<A> source);
            }

            @Override
            public <R> R match(Fn1<? super Pure<B>, ? extends R> aFn,
                               Fn1<? super Impure<B>, ? extends R> bFn,
                               Fn1<? super Zipped<?, B>, ? extends R> cFn,
                               Fn1<? super FlatMapped<?, B>, ? extends R> dFn) {
                return cFn.apply(this);
            }

            @Override
            RecursiveResult<Progress<?, B>, Future<B>> resumeAsync(Executor executor) {
                RecursiveResult<Progress<?, A>, Future<A>> trampoline = trampoline(
                    bodyA -> bodyA.match(
                        pureA -> terminate(pureA.resumeAsync(executor)),
                        impureA -> terminate(impureA.resumeAsync(executor)),
                        zippedA -> zippedA.eliminate(new Phi<RecursiveResult<Body<A>, RecursiveResult<Progress<?, A>, Future<A>>>, A>() {
                            @Override
                            public <Z> RecursiveResult<Body<A>, RecursiveResult<Progress<?, A>, Future<A>>> eliminate(
                                Body<Z> bodyZ, Body<Fn1<? super Z, ? extends A>> bodyZA) {
                                return bodyZ.match(
                                    pureZ -> AsyncPhis.pureWithZip(pureZ, bodyZA),
                                    impureZ -> terminate(recurse(new Progress<>(Future.start(impureZ.computation, executor), left(bodyZA)))),
                                    zippedZ -> AsyncPhis.zippedWithZip(zippedZ, bodyZA),
                                    flatMappedZ -> AsyncPhis.flatMappedWithZip(flatMappedZ, bodyZA)
                                );
                            }
                        }),
                        flatMappedA -> {
                            FlatMapped.Phi<RecursiveResult<Body<A>, RecursiveResult<Progress<?, A>, Future<A>>>, A> phi1 =
                                new FlatMapped.Phi<RecursiveResult<Body<A>, RecursiveResult<Progress<?, A>, Future<A>>>, A>() {
                                    @Override
                                    public <Z> RecursiveResult<Body<A>, RecursiveResult<Progress<?, A>, Future<A>>> eliminate(
                                        Body<Z> bodyZ, Fn1<? super Z, ? extends Body<A>> zBodyA) {
                                        return bodyZ.match(
                                            pureZ -> AsyncPhis.pureWithFlatMap(pureZ, zBodyA),
                                            impureZ -> terminate(recurse(new Progress<>(Future.start(impureZ.computation, executor), right(zBodyA)))),
                                            zippedZ -> AsyncPhis.zippedWithFlatMap(zippedZ, zBodyA),
                                            flatMappedZ -> AsyncPhis.flatMappedWithFlatMap(flatMappedZ, zBodyA)
                                        );
                                    }
                                };
                            return flatMappedA.eliminate(phi1);
                        }
                    ),
                    source);

                return trampoline.match(
                    progress -> {
                        Progress.Phi<A, RecursiveResult<Progress<?, B>, Future<B>>> phi =
                            new Progress.Phi<A, RecursiveResult<Progress<?, B>, Future<B>>>() {
                                @Override
                                public <Z> RecursiveResult<Progress<?, B>, Future<B>> eliminate(
                                    Future<Z> futureZ, Body<Fn1<? super Z, ? extends A>> bodyZA) {
                                    return recurse(new Progress<>(
                                        futureZ, left(zipped(bodyZA, flatMapped(
                                        fn, ab -> pure(za -> z -> ab.apply(za.apply(z))))))));
                                }
                            };
                        Progress.Psi<A, RecursiveResult<Progress<?, B>, Future<B>>> psi =
                            new Progress.Psi<A, RecursiveResult<Progress<?, B>, Future<B>>>() {
                                @Override
                                public <Z> RecursiveResult<Progress<?, B>, Future<B>> eliminate(
                                    Future<Z> futureZ, Fn1<? super Z, ? extends Body<A>> zBodyA) {
                                    //todo: could theoretically immediately zip with fn here if needed
                                    return recurse(new Progress<>(
                                        futureZ, right(z -> zipped(zBodyA.apply(z), fn))));
                                }
                            };
                        return progress.eliminate(phi, psi);
                    },
                    futureA -> recurse(new Progress<>(futureA, left(fn))));
            }
        }


        public static final class FlatMapped<A, B> extends Body<B> {
            public final Body<A>                           source;
            public final Fn1<? super A, ? extends Body<B>> fn;

            private FlatMapped(Body<A> source, Fn1<? super A, ? extends Body<B>> fn) {
                this.source = source;
                this.fn = fn;
            }

            @Override
            public <R> R match(Fn1<? super Pure<B>, ? extends R> aFn,
                               Fn1<? super Impure<B>, ? extends R> bFn,
                               Fn1<? super Zipped<?, B>, ? extends R> cFn,
                               Fn1<? super FlatMapped<?, B>, ? extends R> dFn) {
                return dFn.apply(this);
            }

            public <R> R eliminate(FlatMapped.Phi<R, B> phi) {
                return phi.eliminate(source, fn);
            }

            public interface Phi<R, B> {
                <A> R eliminate(Body<A> source, Fn1<? super A, ? extends Body<B>> bodyFn);
            }
        }
    }
}
