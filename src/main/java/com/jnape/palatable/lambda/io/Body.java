package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.adt.Either;
import com.jnape.palatable.lambda.adt.coproduct.CoProduct4;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static com.jnape.palatable.lambda.adt.Either.left;
import static com.jnape.palatable.lambda.adt.Either.right;
import static com.jnape.palatable.lambda.functions.builtin.fn1.Id.id;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.functions.recursion.Trampoline.trampoline;
import static com.jnape.palatable.lambda.io.Unwind.leftMost;
import static java.util.concurrent.ForkJoinPool.commonPool;

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

    final Either<Progress<?, A>, Future<A>> resumeAsync(Executor executor) {
        return leftMost(this)
            .match(pureA -> right(Future.completed(pureA.value)),
                   impureA -> right(Future.start(impureA.computation, executor)),
                   zippedA -> zippedA.eliminate(new Zipped.Phi<Either<Progress<?, A>, Future<A>>, A>() {
                       @Override
                       public <Z> Either<Progress<?, A>, Future<A>> eliminate(
                           Body<Z> bodyZ, Body<Fn1<? super Z, ? extends A>> bodyZA) {
                           return left(bodyZ.resumeAsync(executor)
                                           .match(progressZ -> {
                                                      throw new IllegalStateException("wut");
                                                  },
                                                  futureZ -> new Progress<>(futureZ, left(bodyZA))));
                       }
                   }),
                   flatMappedA -> flatMappedA.eliminate(new FlatMapped.Phi<Either<Progress<?, A>, Future<A>>, A>() {
                       @Override
                       public <Z> Either<Progress<?, A>, Future<A>> eliminate(
                           Body<Z> bodyZ, Fn1<? super Z, ? extends Body<A>> zBodyA) {
                           return left(bodyZ.resumeAsync(executor)
                                           .match(progressZ -> {
                                                      throw new IllegalStateException("wut");
                                                  },
                                                  futureZ -> new Progress<>(futureZ, right(zBodyA))));
                       }
                   }));
    }

    final A unsafeRunSync() {
        return resume().recover(trampoline(body -> body.resume()
            .match(RecursiveResult::recurse,
                   RecursiveResult::terminate)));
    }

    final CompletableFuture<A> unsafeRunAsync(Executor executor) {
        return resumeAsync(executor)
            .match(trampoline(progress -> progress.eliminate(AsyncPhis.unwrapProgressPhi(executor),
                                                             AsyncPhis.unwrapProgressPsi(executor))),
                   id())
            .unsafeRun();
    }

    final CompletableFuture<A> unsafeRunAsync() {
        return unsafeRunAsync(commonPool());
    }

    static <A> Body<A> pure(A a) {
        return new Pure<>(a);
    }

    static <A> Body<A> impure(Fn0<A> fn) {
        return new Impure<>(fn);
    }

    static <A, B> Body<B> zipped(Body<A> body, Body<Fn1<? super A, ? extends B>> bodyFn) {
        return body.projectA()
            .<Body<B>>zip(bodyFn.projectA().fmap(pureF -> pureA -> pure(pureF.value.apply(pureA.value))))
            .orElse(new Zipped<>(body, bodyFn));
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
            return "Pure{value=" + value + '}';
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
