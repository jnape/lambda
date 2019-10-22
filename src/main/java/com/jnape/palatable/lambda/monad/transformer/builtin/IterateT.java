package com.jnape.palatable.lambda.monad.transformer.builtin;

import com.jnape.palatable.lambda.adt.Maybe;
import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.adt.hlist.Tuple2;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.Fn2;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;
import com.jnape.palatable.lambda.functions.specialized.Pure;
import com.jnape.palatable.lambda.internal.data.ImmutableStack;
import com.jnape.palatable.lambda.io.IO;
import com.jnape.palatable.lambda.monad.Monad;
import com.jnape.palatable.lambda.monad.MonadRec;
import com.jnape.palatable.lambda.monad.transformer.MonadT;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static com.jnape.palatable.lambda.adt.Maybe.just;
import static com.jnape.palatable.lambda.adt.Maybe.nothing;
import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.adt.hlist.HList.tuple;
import static com.jnape.palatable.lambda.functions.builtin.fn1.Reverse.reverse;
import static com.jnape.palatable.lambda.functions.builtin.fn2.Into.into;
import static com.jnape.palatable.lambda.functions.builtin.fn2.Iterate.iterate;
import static com.jnape.palatable.lambda.functions.builtin.fn2.Map.map;
import static com.jnape.palatable.lambda.functions.builtin.fn2.Take.take;
import static com.jnape.palatable.lambda.functions.builtin.fn2.Tupler2.tupler;
import static com.jnape.palatable.lambda.functions.builtin.fn3.FoldLeft.foldLeft;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.io.IO.io;
import static com.jnape.palatable.lambda.monad.transformer.builtin.MaybeT.maybeT;
import static java.util.Arrays.asList;

//todo: traversable?
public abstract class IterateT<M extends MonadRec<?, M>, A> implements MonadT<M, A, IterateT<M, ?>, IterateT<?, ?>> {

    public abstract <MMTA extends MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M>> MMTA runIterateT();

    public final IterateT<M, A> concat(IterateT<M, A> other) {
        return new Concat<>(this, other);
    }

    public final IterateT<M, A> cons(MonadRec<A, M> ma) {
        return new Cons<>(ma, this);
    }

    @Override
    public <B, N extends MonadRec<?, N>> IterateT<N, B> lift(MonadRec<B, N> mb) {
        return iterateT(mb.fmap(b -> just(tuple(b, empty(Pure.of(mb))))));
    }

    //todo: nah, trampolineM impl or bust
    public <MA extends MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M>> IterateT<M, A> remap(
            Fn1<? super MA, ? extends MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M>> f) {
        MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M> maybeMMonadRec = runIterateT();

        return iterateT(f.apply(maybeT(maybeMMonadRec).fmap(t -> t.fmap(it -> it.remap(f))).runMaybeT()));
    }

    @Override
    public <B> IterateT<M, B> fmap(Fn1<? super A, ? extends B> fn) {
        return MonadT.super.<B>fmap(fn).coerce();
    }

    @Override
    public <B> IterateT<M, B> flatMap(Fn1<? super A, ? extends Monad<B, IterateT<M, ?>>> f) {
        MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M> mmta = runIterateT();
        MaybeT<M, Tuple2<B, IterateT<M, B>>> mObjectMaybeT = maybeT(mmta)
                .fmap(tupler(IterateT.<M, B>empty(Pure.of(mmta))))
                .trampolineM(into((out, in) -> in.into((head, tail) -> {
                    IterateT<M, B> newOut = out.concat(f.apply(head).coerce());
                    return maybeT(tail.runIterateT().flatMap(
                            maybeMore -> maybeMore
                                    .match(__ -> newOut.runIterateT().fmap(m -> m.fmap(RecursiveResult::terminate)),
                                           more -> mmta.pure(just(recurse(tuple(newOut, more)))))));
                })));
        return iterateT(mObjectMaybeT.runMaybeT());
    }

    @Override
    public <B> IterateT<M, B> pure(B b) {
        MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M> mmta = runIterateT();
        return iterateT(mmta.pure(just(tuple(b, empty(Pure.of(mmta))))));
    }

    @Override
    public <B> IterateT<M, B> trampolineM(
            Fn1<? super A, ? extends MonadRec<RecursiveResult<A, B>, IterateT<M, ?>>> fn) {
        return new Trampoline<>(this, fn);
    }

    public final <B, MB extends MonadRec<B, M>> MB fold(Fn2<? super B, ? super A, ? extends MonadRec<B, M>> fn,
                                                        MonadRec<B, M> acc) {
        return acc.fmap(tupler(this))
                .trampolineM(into((as, b) -> as.runIterateT().flatMap(maybeMore -> maybeMore.match(
                        __ -> acc.pure(terminate(b)),
                        into((head, tail) -> fn.apply(b, head).fmap(tupler(tail)).fmap(RecursiveResult::recurse))))))
                .coerce();
    }

    public final <MU extends MonadRec<Unit, M>> MU forEach(Fn1<? super A, ? extends MonadRec<Unit, M>> fn) {
        return fold((__, a) -> fn.apply(a), runIterateT().pure(UNIT));
    }

    public static <M extends MonadRec<?, M>, A> IterateT<M, A> iterateT(
            MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M> mmta) {
        return new Wrap<>(mmta);
    }

    public static <M extends MonadRec<?, M>, A> IterateT<M, A> of(MonadRec<Collection<A>, M> mas) {


        return iterateT(mas
                                .fmap(map(a -> singleton(mas.pure(a))))
                                .fmap(foldLeft(IterateT::concat, IterateT.<M, A>empty(Pure.of(mas))))
                                .flatMap(IterateT::<MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M>>runIterateT));
    }


    public static void main(String[] args) throws IOException {
        System.in.read();
        Iterator<Integer>        it  = iterate(x -> x + 1, 1).iterator();
        IterateT<IO<?>, Integer> itT = fromIterator(it);

        System.out.println("one");
        Fn1<Integer, MonadRec<Unit, IO<?>>> printTenThousandth = x -> io(() -> {
            if (x % 500_000 == 0)
                System.out.println(x);
        });
        itT.<IO<Unit>>forEach(printTenThousandth).unsafePerformIO();
        System.in.read();
        System.out.println("two");
        itT.<IO<Unit>>forEach(printTenThousandth).unsafePerformIO();
    }


    @SafeVarargs
    public static <M extends MonadRec<?, M>, A> IterateT<M, A> ofThese(MonadRec<A, M> ma, MonadRec<A, M>... mas) {
        @SuppressWarnings("varargs")
        List<MonadRec<A, M>> as = asList(mas);
        return foldLeft(IterateT::cons,
                        empty(Pure.of(ma)),
                        reverse(com.jnape.palatable.lambda.functions.builtin.fn2.Cons.cons(ma, as)));
    }

    public static <M extends MonadRec<?, M>, A> IterateT<M, A> empty(Pure<M> pureM) {
        return new Empty<>(pureM);
    }

    public static <M extends MonadRec<?, M>, A> IterateT<M, A> singleton(MonadRec<A, M> ma) {
        return IterateT.<M, A>empty(Pure.of(ma)).cons(ma);
    }

    public static <A> IterateT<IO<?>, A> fromIterator(Iterator<A> iterator) {
        return iterateT(io(() -> iterator.hasNext()
                                 ? just(tuple(iterator.next(), fromIterator(iterator)))
                                 : nothing()));
    }

    private static final class Wrap<M extends MonadRec<?, M>, A> extends IterateT<M, A> {
        private final MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M> mmta;

        private Wrap(MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M> mmta) {
            this.mmta = mmta;
        }

        @Override
        public <MMTA extends MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M>> MMTA runIterateT() {
            return mmta.coerce();
        }
    }

    private static final class Empty<M extends MonadRec<?, M>, A> extends IterateT<M, A> {
        private final Pure<M> pureM;

        private Empty(Pure<M> pureM) {
            this.pureM = pureM;
        }

        @Override
        public <MMTA extends MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M>> MMTA runIterateT() {
            return pureM.apply(nothing());
        }
    }

    private static final class Cons<M extends MonadRec<?, M>, A> extends IterateT<M, A> {
        private final MonadRec<A, M> head;
        private final IterateT<M, A> tail;

        private Cons(MonadRec<A, M> head, IterateT<M, A> tail) {
            this.head = head;
            this.tail = tail;
        }

        @Override
        public <MMTA extends MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M>> MMTA runIterateT() {
            return head.fmap(a -> just(tuple(a, tail))).coerce();
        }
    }

    private static final class Concat<M extends MonadRec<?, M>, A> extends IterateT<M, A> {
        private final IterateT<M, A> front;
        private final IterateT<M, A> back;

        private Concat(IterateT<M, A> front, IterateT<M, A> back) {
            this.front = front;
            this.back = back;
        }

        @Override
        public <MMTA extends MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M>> MMTA runIterateT() {
            MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M> mmta = front.runIterateT();
            return mmta.flatMap(maybeMore -> maybeMore
                    .match(__ -> back.runIterateT(),
                           more -> mmta.pure(just(more.fmap(moreTail -> moreTail.concat(back))))))
                    .coerce();
        }
    }

    private static final class Trampoline<M extends MonadRec<?, M>, A, B> extends IterateT<M, B> {
        private final Pure<M>                                                                   pureM;
        private final ImmutableStack<IterateT<M, RecursiveResult<A, B>>>                        queued;
        private final Fn1<? super A, ? extends MonadRec<RecursiveResult<A, B>, IterateT<M, ?>>> f;

        private Trampoline(IterateT<M, A> as,
                           Fn1<? super A, ? extends MonadRec<RecursiveResult<A, B>, IterateT<M, ?>>> f) {
            this(Pure.of(as.<MonadRec<Maybe<Tuple2<A, IterateT<M, A>>>, M>>runIterateT()),
                 ImmutableStack.<IterateT<M, RecursiveResult<A, B>>>empty()
                         .push(as.fmap(RecursiveResult::recurse)),
                 f);
        }

        private Trampoline(Pure<M> pureM,
                           ImmutableStack<IterateT<M, RecursiveResult<A, B>>> queued,
                           Fn1<? super A, ? extends MonadRec<RecursiveResult<A, B>, IterateT<M, ?>>> f) {
            this.pureM = pureM;
            this.queued = queued;
            this.f = f;
        }

        @Override
        public <MMTA extends MonadRec<Maybe<Tuple2<B, IterateT<M, B>>>, M>> MMTA runIterateT() {
            MonadRec<Maybe<Tuple2<B, IterateT<M, B>>>, M> res =
                    pureM.<ImmutableStack<IterateT<M, RecursiveResult<A, B>>>, MonadRec<ImmutableStack<IterateT<M, RecursiveResult<A, B>>>, M>>apply(queued)
                            .trampolineM(q -> q.head().match(
                                    __ -> pureM.apply(terminate(nothing())),
                                    it -> it.runIterateT().flatMap(maybeMore -> maybeMore.match(
                                            __ -> pureM.apply(terminate(nothing())),
                                            into((ab, abs) -> ab.match(
                                                    a -> pureM.apply(recurse(queued.push(f.apply(a).coerce()))),
                                                    b -> pureM.apply(terminate(
                                                            just(tuple(b, new Trampoline<>(pureM, queued.tail().push(abs), f)))))))
                                    ))
                            ));

            return res.coerce();
        }
    }
}
