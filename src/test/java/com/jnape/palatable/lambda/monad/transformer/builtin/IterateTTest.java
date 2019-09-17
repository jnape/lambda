package com.jnape.palatable.lambda.monad.transformer.builtin;

import com.jnape.palatable.lambda.functor.builtin.Identity;
import com.jnape.palatable.traitor.annotations.TestTraits;
import com.jnape.palatable.traitor.framework.Subjects;
import com.jnape.palatable.traitor.runners.Traits;
import org.junit.runner.RunWith;
import testsupport.traits.ApplicativeLaws;
import testsupport.traits.Equivalence;
import testsupport.traits.FunctorLaws;
import testsupport.traits.MonadLaws;
import testsupport.traits.MonadRecLaws;

import java.util.ArrayList;

import static com.jnape.palatable.lambda.functor.builtin.Identity.pureIdentity;
import static com.jnape.palatable.lambda.monad.transformer.builtin.IterateT.empty;
import static com.jnape.palatable.traitor.framework.Subjects.subjects;
import static testsupport.traits.Equivalence.equivalence;

@RunWith(Traits.class)
public class IterateTTest {

    @TestTraits({FunctorLaws.class, ApplicativeLaws.class, MonadLaws.class, MonadRecLaws.class})
    public Subjects<Equivalence<IterateT<Identity<?>, Object>>> testSubject() {
        return subjects(equivalence(empty(pureIdentity()), it -> it.fold((xs, x) -> {
            xs.add(x);
            return new Identity<>(xs);
        }, new ArrayList<>())));
    }
}