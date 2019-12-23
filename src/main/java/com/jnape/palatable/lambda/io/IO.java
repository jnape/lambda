package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.adt.Either;
import com.jnape.palatable.lambda.adt.Try;
import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.adt.choice.Choice2;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.Fn2;
import com.jnape.palatable.lambda.functions.builtin.fn2.LazyRec;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;
import com.jnape.palatable.lambda.functions.specialized.Pure;
import com.jnape.palatable.lambda.functions.specialized.SideEffect;
import com.jnape.palatable.lambda.functor.Applicative;
import com.jnape.palatable.lambda.functor.builtin.Lazy;
import com.jnape.palatable.lambda.monad.Monad;
import com.jnape.palatable.lambda.monad.MonadError;
import com.jnape.palatable.lambda.monad.MonadRec;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jnape.palatable.lambda.adt.Either.left;
import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.adt.choice.Choice2.a;
import static com.jnape.palatable.lambda.adt.choice.Choice2.b;
import static com.jnape.palatable.lambda.functions.Fn0.fn0;
import static com.jnape.palatable.lambda.functions.builtin.fn1.Constantly.constantly;
import static com.jnape.palatable.lambda.functions.builtin.fn1.Downcast.downcast;
import static com.jnape.palatable.lambda.functions.builtin.fn2.Eq.eq;
import static com.jnape.palatable.lambda.functor.builtin.Lazy.lazy;
import static com.jnape.palatable.lambda.monad.Monad.join;
import static java.lang.Thread.interrupted;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.ForkJoinPool.commonPool;

/**
 * A {@link Monad} representing some side-effecting computation to be performed. Note that because {@link IO} inherently
 * offers an interface supporting parallelism, the optimal execution strategy for any given {@link IO} is encoded in
 * its composition.
 *
 * @param <A> the result type
 */
public abstract class IO<A> implements MonadRec<A, IO<?>>, MonadError<Throwable, A, IO<?>> {

    private IO() {
    }

    public static <A> IO<A> temporary(Fn0<A> sync, Fn1<Executor, CompletableFuture<A>> async) {
        return new IO<A>() {
            @Override
            public A unsafePerformIO() {
                return sync.apply();
            }

            @Override
            public CompletableFuture<A> unsafePerformAsyncIO(Executor executor) {
                return async.apply(executor);
            }
        };
    }

    /**
     * Run the effect represented by this {@link IO} instance, blocking the current thread until the effect terminates.
     *
     * @return the result of the effect
     */
    public abstract A unsafePerformIO();

    /**
     * Returns a {@link CompletableFuture} representing the result of this eventual effect. By default, this will
     * immediately run the effect in terms of the implicit {@link Executor} available to {@link CompletableFuture}
     * (usually the {@link java.util.concurrent.ForkJoinPool}). Note that specific {@link IO} constructions may allow
     * this method to delegate to externally-managed {@link CompletableFuture} instead of synthesizing their own.
     *
     * @return the {@link CompletableFuture} representing this {@link IO}'s eventual result
     * @see IO#unsafePerformAsyncIO(Executor)
     */
    public final CompletableFuture<A> unsafePerformAsyncIO() {
        return unsafePerformAsyncIO(commonPool());
    }

    /**
     * Returns a {@link CompletableFuture} representing the result of this eventual effect. By default, this will
     * immediately run the effect in terms of the provided {@link Executor}. Note that specific {@link IO}
     * constructions may allow this method to delegate to externally-managed {@link CompletableFuture} instead of
     * synthesizing their own.
     *
     * @param executor the {@link Executor} to run the {@link CompletableFuture} from
     * @return the {@link CompletableFuture} representing this {@link IO}'s eventual result
     * @see IO#unsafePerformAsyncIO()
     */
    public abstract CompletableFuture<A> unsafePerformAsyncIO(Executor executor);

    /**
     * Given a function from any {@link Throwable} to the result type <code>A</code>, if this {@link IO} successfully
     * yields a result, return it; otherwise, map the {@link Throwable} to the result type and return that.
     *
     * @param recoveryFn the recovery function
     * @return the guarded {@link IO}
     * @deprecated in favor of canonical {@link IO#catchError(Fn1)}
     */
    @Deprecated
    public final IO<A> exceptionally(Fn1<? super Throwable, ? extends A> recoveryFn) {
        return catchError(t -> io(recoveryFn.apply(t)));
    }

    /**
     * Return an {@link IO} that will run <code>ensureIO</code> strictly after running this {@link IO} regardless of
     * whether this {@link IO} terminates normally, analogous to a finally block.
     *
     * @param ensureIO the {@link IO} to ensure runs strictly after this {@link IO}
     * @return the combined {@link IO}
     */
    public final IO<A> ensuring(IO<?> ensureIO) {
        return join(fmap(a -> ensureIO.fmap(constantly(a)))
                        .catchError(t1 -> ensureIO
                            .fmap(constantly(IO.<A>throwing(t1)))
                            .catchError(t2 -> io(io(() -> {
                                t1.addSuppressed(t2);
                                throw t1;
                            })))));
    }

    /**
     * Return a safe {@link IO} that will never throw by lifting the result of this {@link IO} into {@link Either},
     * catching any {@link Throwable} and wrapping it in a {@link Either#left(Object) left}.
     *
     * @return the safe {@link IO}
     */
    public final IO<Either<Throwable, A>> safe() {
        return fmap(Either::<Throwable, A>right).catchError(t -> io(left(t)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final <B> IO<B> pure(B b) {
        return io(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final <B> IO<B> fmap(Fn1<? super A, ? extends B> fn) {
        return MonadError.super.<B>fmap(fn).coerce();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final <B> IO<B> zip(Applicative<Fn1<? super A, ? extends B>, IO<?>> appFn) {
        return new Compose<>(this, a((IO<Fn1<? super A, ? extends B>>) appFn));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final <B> Lazy<IO<B>> lazyZip(Lazy<? extends Applicative<Fn1<? super A, ? extends B>, IO<?>>> lazyAppFn) {
        return MonadError.super.lazyZip(lazyAppFn).<IO<B>>fmap(Monad<B, IO<?>>::coerce).coerce();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final <B> IO<B> discardL(Applicative<B, IO<?>> appB) {
        return MonadError.super.discardL(appB).coerce();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final <B> IO<A> discardR(Applicative<B, IO<?>> appB) {
        return MonadError.super.discardR(appB).coerce();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final <B> IO<B> flatMap(Fn1<? super A, ? extends Monad<B, IO<?>>> f) {
        @SuppressWarnings({"unchecked", "RedundantCast"})
        Choice2<IO<?>, Fn1<Object, IO<?>>> composition = b((Fn1<Object, IO<?>>) (Object) f);
        return new Compose<>(this, composition);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <B> IO<B> trampolineM(Fn1<? super A, ? extends MonadRec<RecursiveResult<A, B>, IO<?>>> fn) {
        return flatMap(a -> fn.apply(a).<IO<RecursiveResult<A, B>>>coerce().flatMap(aOrB -> aOrB.match(
            a_ -> io(a_).trampolineM(fn),
            IO::io)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IO<A> throwError(Throwable throwable) {
        return IO.throwing(throwable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IO<A> catchError(Fn1<? super Throwable, ? extends Monad<A, IO<?>>> recoveryFn) {
        return new IO<A>() {
            @Override
            public A unsafePerformIO() {
                return Try.trying(IO.this::unsafePerformIO)
                    .catchError(t -> Try.trying(recoveryFn.apply(t).<IO<A>>coerce()::unsafePerformIO))
                    .orThrow();
            }

            @Override
            public CompletableFuture<A> unsafePerformAsyncIO(Executor executor) {
                return IO.this.unsafePerformAsyncIO(executor)
                    .handle((a, t) -> t == null
                        ? completedFuture(a)
                        : recoveryFn.apply(t).<IO<A>>coerce().unsafePerformAsyncIO(executor))
                    .thenCompose(f -> f);
            }
        };
    }

    /**
     * Produce an {@link IO} that throws the given {@link Throwable} when executed.
     *
     * @param t   the {@link Throwable}
     * @param <A> any result type
     * @return the {@link IO}
     */
    public static <A> IO<A> throwing(Throwable t) {
        return io(() -> {throw t;});
    }

    /**
     * Wrap the given {@link IO} in an {@link IO} that first checks if the {@link Thread#currentThread() thread} the
     * {@link IO} runs on is {@link Thread#interrupted() interrupted}. If it is, an {@link InterruptedException} is
     * thrown; otherwise the given {@link IO} is executed as usual. Note that for {@link IO}s supporting parallelism,
     * the thread that is checked for interruption may not necessarily be the same thread that the {@link IO} ultimately
     * runs on.
     *
     * @param io  the {@link IO} to wrap
     * @param <A> the {@link IO} result type
     * @return an {@link IO} that first checks for {@link Thread#interrupted() thread interrupts}
     */
    public static <A> IO<A> interruptible(IO<A> io) {
        return join(io(() -> {
            if (interrupted())
                throw new InterruptedException();
            return io;
        }));
    }

    /**
     * <a href="https://docs.oracle.com/javase/tutorial/essential/concurrency/locksync.html">Synchronize</a> the given
     * {@link IO} using the provided lock object. Note that to ensure that the entirety of the {@link IO}'s computation
     * actually runs inside the synchronized region, the {@link IO} is executed
     * {@link IO#unsafePerformIO() synchronously} inside the synchronized block regardless of the caller's chosen
     * execution strategy.
     *
     * @param lock the lock object
     * @param io   the {@link IO}
     * @param <A>  the {@link IO} result type
     * @return the synchronized {@link IO}
     */
    public static <A> IO<A> monitorSync(Object lock, IO<A> io) {
        return io(() -> {
            synchronized (lock) {
                return io.unsafePerformIO();
            }
        });
    }

    /**
     * Fuse all fork opportunities of a given {@link IO} such that, unless it is {@link IO#pin(IO, Executor) pinned}
     * (or is originally {@link IO#externallyManaged(Fn0) externally managed}), no parallelism will be used when
     * running it, regardless of what semantics are used when it is executed.
     *
     * @param io  the {@link IO}
     * @param <A> the {@link IO} result type
     * @return the fused {@link IO}
     * @see IO#pin(IO, Executor)
     */
    public static <A> IO<A> fuse(IO<A> io) {
        return io(io::unsafePerformIO);
    }

    /**
     * Pin an {@link IO} to an {@link Executor} such that regardless of what future decisions are made, when it runs, it
     * will run using whatever parallelism is supported by the {@link Executor}'s threading model. Note that if this
     * {@link IO} has already been pinned (or is originally {@link IO#externallyManaged(Fn0) externally managed}),
     * pinning to an additional {@link Executor} has no meaningful effect.
     *
     * @param io       the {@link IO}
     * @param executor the {@link Executor}
     * @param <A>      the {@link IO} result type
     * @return the {@link IO} pinned to the {@link Executor}
     * @see IO#fuse(IO)
     */
    public static <A> IO<A> pin(IO<A> io, Executor executor) {
        return IO.externallyManaged(() -> io.unsafePerformAsyncIO(executor));
    }

    /**
     * Given an {@link IO}, return an {@link IO} that wraps it, caches its first successful result, and guarantees that
     * no subsequent interactions will happen with it afterwards, returning the cached result thereafter. Note that if
     * the underlying {@link IO} throws, the failure will not be cached, so subsequent interactions with the memoized
     * {@link IO} will again call through to the delegate until it completes normally.
     *
     * @param io  the delegate {@link IO}
     * @param <A> the return type
     * @return the memoized {@link IO}
     */
    public static <A> IO<A> memoize(IO<A> io) {
        class Ref {
            A       value;
            boolean computed;
        }
        Ref ref = new Ref();
        return join(io(() -> {
            if (!ref.computed) {
                return monitorSync(ref, io(() -> {
                    if (!ref.computed) {
                        ref.value = io.unsafePerformIO();
                        ref.computed = true;
                    }
                })).flatMap(constantly(io(() -> ref.value)));
            }
            return io(ref.value);
        }));
    }

    /**
     * Static factory method for creating an {@link IO} that just returns <code>a</code> when performed.
     *
     * @param a   the result
     * @param <A> the result type
     * @return the {@link IO}
     */
    public static <A> IO<A> io(A a) {
        return new IO<A>() {
            @Override
            public A unsafePerformIO() {
                return a;
            }

            @Override
            public CompletableFuture<A> unsafePerformAsyncIO(Executor executor) {
                return completedFuture(a);
            }
        };
    }

    /**
     * Static factory method for coercing a lambda to an {@link IO}.
     *
     * @param fn0 the lambda to coerce
     * @param <A> the result type
     * @return the {@link IO}
     */
    public static <A> IO<A> io(Fn0<? extends A> fn0) {
        return new IO<A>() {
            @Override
            public A unsafePerformIO() {
                return fn0.apply();
            }

            @Override
            public CompletableFuture<A> unsafePerformAsyncIO(Executor executor) {
                return supplyAsync(fn0::apply, executor);
            }
        };
    }

    /**
     * Static factory method for creating an {@link IO} that runs a {@link SideEffect} and returns {@link Unit}.
     *
     * @param sideEffect the {@link SideEffect}
     * @return the {@link IO}
     */
    public static IO<Unit> io(SideEffect sideEffect) {
        return io(fn0(() -> {
            sideEffect.Ω();
            return UNIT;
        }));
    }

    /**
     * Static factory method for creating an {@link IO} from an externally managed source of
     * {@link CompletableFuture completable futures}.
     * <p>
     * Note that constructing an {@link IO} this way results in no intermediate futures being constructed by either
     * {@link IO#unsafePerformAsyncIO()} or {@link IO#unsafePerformAsyncIO(Executor)}, and {@link IO#unsafePerformIO()}
     * is synonymous with invoking {@link CompletableFuture#get()} on the externally managed future.
     *
     * @param supplier the source of externally managed {@link CompletableFuture completable futures}
     * @param <A>      the result type
     * @return the {@link IO}
     */
    public static <A> IO<A> externallyManaged(Fn0<CompletableFuture<A>> supplier) {
        return new IO<A>() {
            @Override
            public A unsafePerformIO() {
                return fn0(() -> unsafePerformAsyncIO().get()).apply();
            }

            @Override
            public CompletableFuture<A> unsafePerformAsyncIO(Executor executor) {
                return supplier.apply();
            }
        };
    }

    /**
     * The canonical {@link Pure} instance for {@link IO}.
     *
     * @return the {@link Pure} instance
     */
    public static Pure<IO<?>> pureIO() {
        return IO::io;
    }

    private static final class Compose<A> extends IO<A> {

        private final IO<?>                              source;
        private final Choice2<IO<?>, Fn1<Object, IO<?>>> composition;

        private Compose(IO<?> source, Choice2<IO<?>, Fn1<Object, IO<?>>> composition) {
            this.source = source;
            this.composition = composition;
        }

        @Override
        public A unsafePerformIO() {
            Lazy<Object> lazyA = LazyRec.<IO<?>, Object>lazyRec(
                (f, io) -> {
                    if (io instanceof IO.Compose<?>) {
                        Compose<?>   compose = (Compose<?>) io;
                        Lazy<Object> head    = f.apply(compose.source);
                        return compose.composition
                            .match(zip -> head.flatMap(x -> f.apply(zip)
                                       .<Fn1<Object, Object>>fmap(downcast())
                                       .fmap(g -> g.apply(x))),
                                   flatMap -> head.fmap(flatMap).flatMap(f));
                    }
                    return lazy(io::unsafePerformIO);
                },
                this);
            return downcast(lazyA.value());
        }

        @Override
        @SuppressWarnings("unchecked")
        public CompletableFuture<A> unsafePerformAsyncIO(Executor executor) {
            CompletableFuture<Object>       res     = new CompletableFuture<>();
            Lazy<CompletableFuture<Object>> lazyFuture = LazyRec.<IO<?>, CompletableFuture<Object>>lazyRec(
                (f, io) -> {
                    if (io instanceof IO.Compose<?>) {
                        Compose<?>                      compose = (Compose<?>) io;
                        Lazy<CompletableFuture<Object>> head    = f.apply(compose.source);
                        return compose.composition
                            .match(zip -> head.flatMap(futureX -> f.apply(zip)
                                       .fmap(futureF -> IO.zip(futureF, futureX,
                                                               (f2, x) -> ((Fn1<Object, Object>) f2).apply(x),
                                                               executor, res))),
                                   flatMap -> head.fmap(futureX -> {
                                       return IO.flatMap(futureX,
                                                         x -> f.apply(flatMap.apply(x)).value(),
                                                         executor, res);
                                   }));
                    }
                    return lazy(() -> (CompletableFuture<Object>) io.unsafePerformAsyncIO(executor));
                },
                this);

            return (CompletableFuture<A>) lazyFuture.value();
        }
    }

    public static <A, B> IO<B> forever(IO<A> io) {
        return io.flatMap(__ -> forever(io));
    }

    public static <A> IO<A> until(Fn1<? super A, ? extends Boolean> pred, IO<A> io) {
        return io.flatMap(a -> pred.apply(a) ? io(a) : until(pred, io));
    }

    public static void main(String[] args) throws IOException {
        AtomicInteger counter = new AtomicInteger();

        System.in.read();

        until(eq(10000000), io(counter::incrementAndGet))
            .unsafePerformAsyncIO()
            .join();

        System.out.println("done");

//        forever(io(() -> {
//            int count = counter.incrementAndGet();
//            if (count % 100_000 == 0)
//                System.out.println(Thread.currentThread() + ": " + count);
//        })).unsafePerformAsyncIO().join()
//        ;
    }

    private static <A, B> CompletableFuture<B> fmap(CompletableFuture<A> future, Fn1<? super A, ? extends B> fn) {
        CompletableFuture<B> res = new CompletableFuture<>();

        future.whenComplete((a, t) -> {
            if (t == null) {
                try {
                    res.complete(fn.apply(a));
                } catch (Throwable t2) {
                    res.completeExceptionally(t2);
                }
            } else {
                res.completeExceptionally(t);
            }
        });

        return res;
    }

    private static <A, B> CompletableFuture<B> flatMap(CompletableFuture<A> future, Fn1<? super A, ?
        extends CompletableFuture<B>> fn, Executor executor, CompletableFuture<B> res) {
        future.whenCompleteAsync((a, t) -> {
            if (t == null) {
                try {
                    fn.apply(a).whenCompleteAsync((b, t2) -> {
                        if (t2 == null) {
                            res.complete(b);
                        } else {
                            res.completeExceptionally(t2);
                        }
                    }, executor);
                } catch (Throwable t2) {
                    res.completeExceptionally(t2);
                }
            } else {
                res.completeExceptionally(t);
            }
        }, executor);
        return res;
    }

    private static <A, B, C> CompletableFuture<C> zip(CompletableFuture<A> futureA, CompletableFuture<B> futureB,
                                                      Fn2<? super A, ? super B, ? extends C> fn,
                                                      Executor executor, CompletableFuture<C> res) {
        futureA.whenCompleteAsync((a, ta) -> futureB.whenCompleteAsync((b, tb) -> {
            if (ta != null)
                res.completeExceptionally(ta);
            else if (tb != null) {
                res.completeExceptionally(tb);
            } else {
                try {
                    res.complete(fn.apply(a, b));
                } catch (Throwable t) {
                    res.completeExceptionally(t);
                }
            }
        }, executor), executor);
        return res;
    }
}
