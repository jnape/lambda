package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;
import com.jnape.palatable.lambda.functor.Applicative;
import com.jnape.palatable.lambda.monad.Monad;
import com.jnape.palatable.lambda.monad.MonadRec;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static com.jnape.palatable.lambda.functions.builtin.fn3.Times.times;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.io.Futures.thenCombine;
import static com.jnape.palatable.lambda.io.Futures.thenCombineAsync;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;

public class Future<A> implements MonadRec<A, Future<?>> {

    private final CompletableFuture<A> computation;

    public Future(CompletableFuture<A> computation) {
        this.computation = computation;
    }

    public CompletableFuture<A> unsafeRun() {
        return computation;
    }

    @Override
    public <B> Future<B> trampolineM(Fn1<? super A, ? extends MonadRec<RecursiveResult<A, B>, Future<?>>> fn) {
        return flatMap(fn).flatMap(aOrB -> aOrB.match(a -> pure(a).trampolineM(fn), this::pure));
    }

    @Override
    public <B> Future<B> zip(Applicative<Fn1<? super A, ? extends B>, Future<?>> appFn) {
        return new Future<>(thenCombineAsync(unsafeRun(), appFn.<Future<Fn1<? super A, ? extends B>>>coerce().unsafeRun()));
    }

    @Override
    public <B> Future<B> flatMap(Fn1<? super A, ? extends Monad<B, Future<?>>> f) {
        return new Future<>(Futures.thenComposeAsync(unsafeRun(),
                                                     a -> f.apply(a).<Future<B>>coerce().unsafeRun()));
    }

    @Override
    public <B> Future<B> fmap(Fn1<? super A, ? extends B> fn) {
        return MonadRec.super.<B>fmap(fn).coerce();
    }

    @Override
    public <B> Future<B> pure(B b) {
        return new Future<>(completedFuture(b));
    }

    public static <A> Future<A> completed(A a) {
        return new Future<>(completedFuture(a));
    }

    public static <A> Future<A> start(Fn0<A> fn0, Executor executor) {
        return new Future<>(supplyAsync(fn0.toSupplier(), executor));
    }

    public static void main(String[] args) {
        Integer f2 = times(100000, f -> f.zip(new Future<>(completedFuture(x -> x + 1))),
                           new Future<>(completedFuture(1)))
            .unsafeRun()
            .join();
        System.out.println(f2);

        Integer r = new Future<>(completedFuture(1))
            .trampolineM(x -> x < 100000
                ? new Future<>(completedFuture(recurse(x + 1)))
                : new Future<>(completedFuture(terminate(x))))
            .unsafeRun()
            .join();

        System.out.println(r);
    }
}
