package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;

import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.io.Body.*;

public final class Correct {

    // Correct
    static <Z, A, B> RecursiveResult<Body<A>, B> flatMappedWithFlatMap(Body.FlatMapped<?, Z> flatMappedZ,
                                                                       Fn1<? super Z, ? extends Body<A>> zBodyA) {
        return flatMappedZ.eliminate(new Body.FlatMapped.Phi<RecursiveResult<Body<A>, B>, Z>() {
            @Override
            public <Y> RecursiveResult<Body<A>, B> eliminate(Body<Y> bodyY, Fn1<? super Y, ? extends Body<Z>> yBodyZ) {
                return recurse(flatMapped(bodyY, y -> flatMapped(yBodyZ.apply(y), zBodyA)));
            }
        });
    }

    // Correct
    static <Z, A, B> RecursiveResult<Body<A>, B> zippedWithZip(
        Body.Zipped<?, Z> zippedZ,
        Body<Fn1<? super Z, ? extends A>> bodyZA) {
        return zippedZ.eliminate(new Body.Zipped.Phi<RecursiveResult<Body<A>, B>, Z>() {
            @Override
            public <Y> RecursiveResult<Body<A>, B> eliminate(Body<Y> bodyY,
                                                             Body<Fn1<? super Y, ? extends Z>> bodyYZ) {

                return recurse(
                    flatMapped(bodyY, y -> {
                        Body<A> res = flatMapped(bodyYZ, yz -> {

                            try {
                                Z z = yz.apply(y);
                                return flatMapped(bodyZA, za -> {
                                    try {
                                        return pure(za.apply(z));
                                    } catch (StackOverflowError soe) {
                                        return pure(za.apply(z));
                                    }
                                });
                            } catch (StackOverflowError soe) {
                                Z z = yz.apply(y);
                                return flatMapped(bodyZA, za -> {
                                    return pure(za.apply(z));
                                });
                            }
                        });
                        return res;
                    })
                );

//                return recurse(zipped(bodyY, flatMapped(bodyYZ, yz -> {
//                    return flatMapped(bodyZA, za -> pure(y -> za.apply(yz.apply(y))));
//                })));


//                return recurse(zipped(bodyY, zipped(bodyYZ, flatMapped(bodyZA, za -> {
//
//
//
//                    return pure(yz -> y -> za.apply(yz.apply(y)));
//                }))));
            }
        });
    }
}
