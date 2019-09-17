package com.jnape.palatable.lambda.monad.transformer.builtin;

import com.jnape.palatable.lambda.adt.Maybe;
import com.jnape.palatable.lambda.adt.hlist.Tuple2;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.Fn2;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;
import com.jnape.palatable.lambda.functions.specialized.Pure;
import com.jnape.palatable.lambda.functor.builtin.Identity;
import com.jnape.palatable.lambda.monad.Monad;
import com.jnape.palatable.lambda.monad.MonadRec;
import com.jnape.palatable.lambda.monad.transformer.MonadT;

import java.io.IOException;

import static com.jnape.palatable.lambda.adt.Maybe.just;
import static com.jnape.palatable.lambda.adt.Maybe.nothing;
import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.adt.hlist.HList.tuple;
import static com.jnape.palatable.lambda.functions.builtin.fn1.Constantly.constantly;
import static com.jnape.palatable.lambda.functions.builtin.fn2.Into.into;
import static com.jnape.palatable.lambda.functions.builtin.fn2.Tupler2.tupler;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;

public final class IterateT<M extends MonadRec<?, M>, A> implements MonadT<M, A, IterateT<M, ?>, IterateT<?, ?>> {

    private final MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M> mmta;

    private IterateT(MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M> mmta) {
        this.mmta = mmta;
    }

    public <MMTA extends MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M>> MMTA runIterateT() {
        return mmta.coerce();
    }

    public IterateT<M, A> cons(MonadRec<A, M> ma) {
        return iterateT(ma.fmap(a -> just(tuple(a, this))));
    }

    public <B, MB extends MonadRec<B, M>> MB fold(Fn2<? super B, ? super A, ? extends MonadRec<B, M>> fn, B acc) {
        return runIterateT().fmap(tupler(acc))
                .trampolineM(into((b, mta) -> mta.match(
                        constantly(mmta.pure(terminate(b))),
                        into((a, more) -> fn.apply(b, a)
                                .flatMap(nextB -> more.runIterateT()
                                        .fmap(tupler(nextB)).fmap(RecursiveResult::recurse))))))
                .coerce();
    }

    @Override
    public <B, N extends MonadRec<?, N>> IterateT<N, B> lift(MonadRec<B, N> mb) {
        return new IterateT<>(mb.fmap(b -> just(tuple(b, empty(Pure.of(mb))))));
    }

    @Override
    public <B> IterateT<M, B> fmap(Fn1<? super A, ? extends B> fn) {
        IterateT<M, B> empty = empty(Pure.of(mmta));
        MonadRec<IterateT<M, B>, M> fold = fold((bs, a) -> {

            return mmta.pure(bs.cons(mmta.pure(fn.apply(a))));
        }, empty);
        return iterateT(fold.flatMap(it -> it.runIterateT()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <B> IterateT<M, B> flatMap(Fn1<? super A, ? extends Monad<B, IterateT<M, ?>>> f) {
        return trampolineM(a -> {
            IterateT<M, B>                                coerce         = f.apply(a).coerce();
            return coerce.fmap(RecursiveResult::terminate);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <B> IterateT<M, B> pure(B b) {
        return lift(runIterateT().pure(b));
    }

    @Override
    public <B> IterateT<M, B> trampolineM(
            Fn1<? super A, ? extends MonadRec<RecursiveResult<A, B>, IterateT<M, ?>>> fn) {
        MonadRec<Tuple2<IterateT<M, B>, Maybe<Tuple2<A, IterateT<M, A>>>>, M> fmap1 = runIterateT().fmap(tupler(IterateT.<M, B>empty(Pure.of(mmta))));
        MonadRec<IterateT<M, B>, M> bar = fmap1
                .trampolineM(into((bs, maybeMore) -> {
                    return maybeMore.match(
                            constantly(mmta.pure(terminate(bs))),
                            into((a, as) -> fn.apply(a).<IterateT<M, RecursiveResult<A, B>>>coerce().runIterateT()
                                    .flatMap(maybeAB -> {
                                        MonadRec<RecursiveResult<Tuple2<IterateT<M, B>, Maybe<Tuple2<A, IterateT<M, A>>>>, IterateT<M, B>>, M> quux = maybeAB.match(
                                                constantly(as.runIterateT().fmap(tail -> recurse(tuple(bs, tail)))),
                                                into((ab, abs) -> {
                                                    return ab.match(a__ -> {
                                                        return as.cons(mmta.pure(a__)).runIterateT().flatMap(moreAs -> {

                                                            return abs.fold((bsAndAs, aOrB) -> {
                                                                return aOrB.match(
                                                                        a_ -> {
                                                                            Tuple2<IterateT<M, B>, IterateT<M, A>> fmap = bsAndAs.fmap(aas -> iterateT(mmta.pure(aas)).cons(mmta.pure(a_)));
                                                                            return fmap._2().runIterateT().fmap(tupler(fmap._1()));
                                                                        },
                                                                        b -> {
                                                                            return mmta.pure(bsAndAs.biMapL(it -> it.cons(mmta.pure(b))));
                                                                        });
                                                            }, tuple(bs, moreAs));
                                                        }).fmap(RecursiveResult::recurse);

                                                    }, b__ -> {
                                                        return as.runIterateT().flatMap(moreAs -> {

                                                            return abs.fold((bsAndAs, aOrB) -> {
                                                                return aOrB.match(
                                                                        a_ -> {
                                                                            Tuple2<IterateT<M, B>, IterateT<M, A>> fmap = bsAndAs.fmap(aas -> iterateT(mmta.pure(aas)).cons(mmta.pure(a_)));
                                                                            return fmap._2().runIterateT().fmap(tupler(fmap._1()));
                                                                        },
                                                                        b -> {
                                                                            return mmta.pure(bsAndAs.biMapL(it -> it.cons(mmta.pure(b))));
                                                                        });
                                                            }, tuple(bs.cons(mmta.pure(b__)), moreAs));
                                                        }).fmap(RecursiveResult::recurse);

                                                    });
                                                }));
                                        return quux;
                                    })));
                }));
        MonadRec<Maybe<Tuple2<B, IterateT<M, B>>>, M> baz = bar.flatMap(IterateT::<MonadRec<Maybe<Tuple2<B, IterateT<M, B>>>, M>>runIterateT);
        return iterateT(baz);
    }

    public static void main(String[] args) throws IOException {
        System.in.read();
        IterateT<Identity<?>, Integer> it = singleton(new Identity<>(1));
        it
                .trampolineM(x -> {
                    if (x % 1_000_000 == 0)
                        System.out.println(x);
                    IterateT<Identity<?>, RecursiveResult<Integer, Integer>> it2 = singleton(new Identity<>(recurse(x + 1)));
                    return x < 100_000_000 ? it2
                                           : singleton(new Identity<>(terminate(x)));
                })
                .fold((xs, a) -> {


                    return new Identity<>(xs);
                }, UNIT);
    }

    public static <M extends MonadRec<?, M>, A> IterateT<M, A> empty(Pure<M> pureM) {
        MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M> pureNothing = pureM.apply(nothing());
        return iterateT(pureNothing);
    }

    public static <M extends MonadRec<?, M>, A> IterateT<M, A> singleton(MonadRec<A, M> ma) {
        return iterateT(ma.fmap(a -> just(tuple(a, empty(Pure.of(ma))))));
    }

    public static <M extends MonadRec<?, M>, A> IterateT<M, A> iterateT(
            MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M> mmta) {
        return new IterateT<>(mmta);
    }
}
