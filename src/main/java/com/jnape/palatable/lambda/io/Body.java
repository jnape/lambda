package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.adt.Either;
import com.jnape.palatable.lambda.adt.coproduct.CoProduct4;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import static com.jnape.palatable.lambda.adt.Either.right;
import static com.jnape.palatable.lambda.functions.builtin.fn1.Id.id;
import static com.jnape.palatable.lambda.functions.builtin.fn3.Times.times;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.functions.recursion.Trampoline.trampoline;
import static com.jnape.palatable.lambda.io.AsyncPhis.unwind;
import static com.jnape.palatable.lambda.io.IO.io;

abstract class Body<A> implements
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

        <R> R eliminate(Progress.Phi<B, R> phi, Progress.Psi<B, R> psi) {
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

    final RecursiveResult<Progress<?, A>, Future<A>> resumeAsync(Executor executor) {
        return unwind(this, executor);
    }

    final A unsafeRunSync() {
        return resume().recover(trampoline(body -> body.resume()
            .match(RecursiveResult::recurse,
                   RecursiveResult::terminate)));
    }

    final CompletableFuture<A> unsafeRunAsync(Executor executor) {
        return resumeAsync(executor)
            .match(trampoline(progress -> progress.eliminate(AsyncPhis.unwrapProgressPhi(executor),
                                                             AsyncPhis.unwrapProgressPsi(executor))), id())
            .unsafeRun();
    }

    public static void main(String[] args) {
        ForkJoinPool ex = ForkJoinPool.commonPool();
        Integer res = times(10, b -> zipped(b, pure(x -> x + 1)), pure(0))
            .unsafeRunAsync(ex)
            .join();
        System.out.println(res);
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
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Pure<?> pure = (Pure<?>) o;
            return Objects.equals(value, pure.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return "Pure{value=" + (value instanceof Fn1<?, ?> ? "f()" : value) + '}';
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
        public String toString() {
            return "Impure{computation=" + computation + '}';
        }
    }

    public static final class Zipped<A, B> extends Body<B> {
        public final Body<A>                           bodyA;
        public final Body<Fn1<? super A, ? extends B>> bodyAB;

        private Zipped(Body<A> bodyA, Body<Fn1<? super A, ? extends B>> bodyAB) {
            this.bodyA = bodyA;
            this.bodyAB = bodyAB;
        }

        public <R> R eliminate(Zipped.Phi<R, B> phi) {
            return phi.eliminate(bodyA, bodyAB);
        }

        public interface Phi<R, B> {
            <A> R eliminate(Body<A> source, Body<Fn1<? super A, ? extends B>> bodyFn);
        }

        @Override
        public <R> R match(Fn1<? super Pure<B>, ? extends R> aFn,
                           Fn1<? super Impure<B>, ? extends R> bFn,
                           Fn1<? super Zipped<?, B>, ? extends R> cFn,
                           Fn1<? super FlatMapped<?, B>, ? extends R> dFn) {
            return cFn.apply(this);
        }

        @Override
        public String toString() {
            return "Zipped{bodyA=" + bodyA + ", bodyAB=" + bodyAB + '}';
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FlatMapped<?, ?> that = (FlatMapped<?, ?>) o;
            return Objects.equals(source, that.source);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, fn);
        }

        @Override
        public String toString() {
            return "FlatMapped{source=" + source + ", fn=" + fn + '}';
        }
    }
}
