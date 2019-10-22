package com.jnape.palatable.lambda.monad.transformer.builtin;

import com.jnape.palatable.lambda.adt.Maybe;
import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functor.builtin.Identity;
import com.jnape.palatable.lambda.functor.builtin.Writer;
import com.jnape.palatable.traitor.annotations.TestTraits;
import com.jnape.palatable.traitor.framework.Subjects;
import com.jnape.palatable.traitor.runners.Traits;
import org.junit.Test;
import org.junit.runner.RunWith;
import testsupport.traits.ApplicativeLaws;
import testsupport.traits.Equivalence;
import testsupport.traits.FunctorLaws;
import testsupport.traits.MonadLaws;
import testsupport.traits.MonadRecLaws;

import java.util.ArrayList;
import java.util.List;

import static com.jnape.palatable.lambda.adt.Maybe.just;
import static com.jnape.palatable.lambda.adt.Maybe.nothing;
import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.adt.hlist.HList.tuple;
import static com.jnape.palatable.lambda.functions.builtin.fn3.Times.times;
import static com.jnape.palatable.lambda.functor.builtin.Identity.pureIdentity;
import static com.jnape.palatable.lambda.functor.builtin.Writer.listen;
import static com.jnape.palatable.lambda.functor.builtin.Writer.pureWriter;
import static com.jnape.palatable.lambda.functor.builtin.Writer.tell;
import static com.jnape.palatable.lambda.functor.builtin.Writer.writer;
import static com.jnape.palatable.lambda.io.IO.io;
import static com.jnape.palatable.lambda.monad.transformer.builtin.IterateT.empty;
import static com.jnape.palatable.lambda.monad.transformer.builtin.IterateT.singleton;
import static com.jnape.palatable.lambda.monad.transformer.builtin.MaybeT.maybeT;
import static com.jnape.palatable.lambda.monad.transformer.builtin.MaybeT.pureMaybeT;
import static com.jnape.palatable.lambda.monoid.builtin.AddAll.addAll;
import static com.jnape.palatable.traitor.framework.Subjects.subjects;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static testsupport.Constants.STACK_EXPLODING_NUMBER;
import static testsupport.matchers.IOMatcher.yieldsValue;
import static testsupport.traits.Equivalence.equivalence;

@RunWith(Traits.class)
public class IterateTTest {

    @TestTraits({FunctorLaws.class, ApplicativeLaws.class, MonadLaws.class, MonadRecLaws.class})
    public Subjects<Equivalence<IterateT<Identity<?>, Object>>> testSubject() {
        Fn1<IterateT<Identity<?>, Object>, Object> collect = it -> it.fold((xs, x) -> {
                                                                               xs.add(x);
                                                                               return new Identity<>(xs);
                                                                           },
                                                                           new Identity<>(new ArrayList<>()));
        return subjects(equivalence(empty(pureIdentity()), collect),
                        equivalence(singleton(new Identity<>(1)), collect));
    }

    @Test
    public void fold() {
        Fn1<Integer, Writer<List<Integer>, Integer>> record = x -> writer(tuple(x, singletonList(x)));
        assertEquals(tuple(6, asList(1, 2, 3)),
                     IterateT.<Writer<List<Integer>, ?>, Integer>empty(pureWriter())
                             .cons(record.apply(3))
                             .cons(record.apply(2))
                             .cons(record.apply(1))
                             .<Integer, Writer<List<Integer>, Integer>>fold((x, y) -> listen(x + y), listen(0))
                             .runWriter(addAll(ArrayList::new)));
    }

    @Test
    public void foldLargeNumberOfElements() {
        IterateT<Identity<?>, Integer> largeIterateT = times(STACK_EXPLODING_NUMBER,
                                                             it -> it.cons(new Identity<>(1)),
                                                             empty(pureIdentity()));
        assertEquals(new Identity<>(STACK_EXPLODING_NUMBER),
                     largeIterateT.fold((x, y) -> new Identity<>(x + y), new Identity<>(0)));
    }

    @Test
    public void foldShortCircuiting() {
        IterateT<MaybeT<Writer<List<Integer>, ?>, ?>, Integer> largeIterateT =
                times(STACK_EXPLODING_NUMBER,
                      it -> it.cons(maybeT(writer(tuple(just(1), singletonList(1))))),
                      empty(pureMaybeT(pureWriter())));

        assertEquals(tuple(nothing(), asList(1, 1, 1, 1)),
                     largeIterateT
                             .<Integer, MaybeT<Writer<List<Integer>, ?>, Integer>>fold(
                                     (acc, x) -> maybeT(listen(acc < 3 ? just(acc + x) : nothing())),
                                     maybeT(listen(just(0))))
                             .<Writer<List<Integer>, Maybe<Integer>>>runMaybeT()
                             .runWriter(addAll(ArrayList::new)));
    }

    @Test
    public void forEach() {
        assertEquals(tuple(UNIT, asList(1, 2, 3)),
                     IterateT.<Writer<List<Integer>, ?>, Integer>empty(pureWriter())
                             .cons(listen(3))
                             .cons(listen(2))
                             .cons(listen(1))
                             .<Writer<List<Integer>, Unit>>forEach(x -> tell(singletonList(x)))
                             .runWriter(addAll(ArrayList::new)));
    }

    @Test
    public void fromIterator() {
        assertThat(IterateT.fromIterator(asList(1, 2, 3).iterator()).fold((x, y) -> io(x + y), io(0)),
                   yieldsValue(equalTo(6)));
    }

    @Test
    public void of() {
        assertEquals(tuple(6, asList(1, 2, 3)),
                     IterateT.<Writer<List<Integer>, ?>, Integer>ofThese(listen(1), listen(2), listen(3))
                             .<Integer, Writer<List<Integer>, Integer>>fold(
                                     (x, y) -> writer(tuple(x + y, singletonList(y))), listen(0))
                             .runWriter(addAll(ArrayList::new)));
    }
}