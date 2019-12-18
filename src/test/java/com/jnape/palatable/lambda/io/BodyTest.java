package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.adt.Either;
import com.jnape.palatable.lambda.functions.Fn1;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import static com.jnape.palatable.lambda.adt.Either.left;
import static com.jnape.palatable.lambda.io.Body.*;
import static org.junit.Assert.assertEquals;

@RunWith(Enclosed.class)
public class BodyTest {

    @RunWith(Enclosed.class)
    public static class FlatMapping {

        public static class Resume {
            Fn1<? super Integer, ? extends Body<Integer>> f = x -> pure(x + 1);
            Body<Fn1<? super Integer, ? extends Integer>> g = pure(x -> x + 1);

            @Test
            public void flatMapPure() {
                assertEquals(left(pure(2)), flatMapped(pure(1), f).resume());
            }

            @Test
            public void flatMapFlatMap() {
                assertEquals(left(flatMapped(pure(2), f)),
                             flatMapped(flatMapped(pure(1), f), f).resume());
            }

            @Test
            public void flatMapZipped() {
                Body<Integer> x = flatMapped(zipped(pure(1), g), f);
                Either<Body<Integer>, Integer> resume = x.resume();
                boolean y = false;
                assertEquals(left(flatMapped(g, null)),
                             flatMapped(zipped(pure(1), g), f).resume());
            }
        }


    }

}