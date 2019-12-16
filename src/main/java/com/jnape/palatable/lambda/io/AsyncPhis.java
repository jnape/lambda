package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;
import com.jnape.palatable.lambda.io.NewIO.Body;

import static com.jnape.palatable.lambda.functions.builtin.fn1.Id.id;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.io.NewIO.Body.*;

class AsyncPhis {

    static <Z, A, B> RecursiveResult<Body<A>, B> zippedWithZip(
        Body.Zipped<?, Z> zippedZ,
        Body<Fn1<? super Z, ? extends A>> bodyZA) {
        return zippedZ.eliminate(new Zipped.Phi<RecursiveResult<Body<A>, B>, Z>() {
            @Override
            public <Y> RecursiveResult<Body<A>, B> eliminate(Body<Y> bodyY,
                                                             Body<Fn1<? super Y, ? extends Z>> bodyYZ) {
                return recurse(zipped(bodyY, zipped(bodyYZ, flatMapped(
                    bodyZA, za -> pure(yz -> y -> za.apply(yz.apply(y)))))));
            }
        });

    }

    static <Z, A, B> RecursiveResult<Body<A>, B> flatMappedWithZip(
        Body.FlatMapped<?, Z> flatMappedZ,
        Body<Fn1<? super Z, ? extends A>> bodyZA) {
        return flatMappedZ.eliminate(new FlatMapped.Phi<RecursiveResult<Body<A>, B>, Z>() {
            @Override
            public <Y> RecursiveResult<Body<A>, B> eliminate(
                Body<Y> bodyY, Fn1<? super Y, ? extends Body<Z>> yBodyZ) {
                return recurse(flatMapped(zipped(
                    bodyZA, zipped(
                        bodyY, pure(y -> za -> flatMapped(
                            yBodyZ.apply(y), z -> pure(za.apply(z)))))), id()));
            }
        });

    }

    static <Z, A, B> RecursiveResult<Body<A>, B> zippedWithFlatMap(
        Body.Zipped<?, Z> zippedZ,
        Fn1<? super Z, ? extends Body<A>> zBodyA) {
        return zippedZ.eliminate(new Zipped.Phi<RecursiveResult<Body<A>, B>, Z>() {
            @Override
            public <Y> RecursiveResult<Body<A>, B> eliminate(Body<Y> bodyY, Body<Fn1<? super Y, ? extends Z>> bodyYZ) {
                return recurse(flatMapped(zipped(bodyY, zipped(bodyYZ, pure(yz -> y -> zBodyA.apply(yz.apply(y))))),
                                          id()));
            }
        });
    }

    static <Z, A, B> RecursiveResult<Body<A>, B> pureWithZip(Pure<Z> pureZ, Body<Fn1<? super Z, ?
        extends A>> bodyZA) {
        return recurse(flatMapped(bodyZA, za -> pure(za.apply(pureZ.value))));
    }

    static <Z, A, B> RecursiveResult<Body<A>, B> pureWithFlatMap(Pure<Z> pureZ, Fn1<? super Z, ?
        extends Body<A>> zBodyA) {
        return recurse(zBodyA.apply(pureZ.value));
    }

    static <Z, A, B> RecursiveResult<Body<A>, B> flatMappedWithFlatMap(Body.FlatMapped<?, Z> flatMappedZ,
                                                                       Fn1<? super Z, ? extends Body<A>> zBodyA) {
        return flatMappedZ.eliminate(new Body.FlatMapped.Phi<RecursiveResult<Body<A>, B>, Z>() {
            @Override
            public <Y> RecursiveResult<Body<A>, B> eliminate(Body<Y> bodyY, Fn1<? super Y, ? extends Body<Z>> yBodyZ) {
                return recurse(flatMapped(bodyY, y -> flatMapped(yBodyZ.apply(y), zBodyA)));
            }
        });
    }
}
