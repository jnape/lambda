package com.jnape.palatable.lambda.io;

import com.jnape.palatable.lambda.adt.Either;
import com.jnape.palatable.lambda.functions.Fn1;

final class Progress<A, B> {
     final Future<A>                                                                    futureA;
     final Either<Body<Fn1<? super A, ? extends B>>, Fn1<? super A, ? extends Body<B>>> composition;

    Progress(Future<A> futureA,
             Either<Body<Fn1<? super A, ? extends B>>, Fn1<? super A, ? extends Body<B>>> composition) {
        this.futureA = futureA;
        this.composition = composition;
    }

    <R> R eliminate(Phi<B, R> phi, Psi<B, R> psi) {
        return composition.match(bodyAB -> phi.eliminate(futureA, bodyAB),
                                 aBodyB -> psi.eliminate(futureA, aBodyB));
    }

    interface Phi<B, R> {
        <A> R eliminate(Future<A> futureA, Body<Fn1<? super A, ? extends B>> bodyAB);
    }

    interface Psi<B, R> {
        <A> R eliminate(Future<A> futureA, Fn1<? super A, ? extends Body<B>> aBodyB);
    }

    @Override
    public String toString() {
        return "Progress{" +
            "futureA=" + futureA +
            ", composition=" + composition +
            '}';
    }

    public <C> Progress<A, C> add(Body<Fn1<? super B, ? extends C>> bodyBC) {
        throw new IllegalStateException("foo");
    }

    public <C> Progress<A, C> add(Fn1<? super B, ? extends Body<C>> bBodyC) {
        throw new IllegalStateException("bar");
    }
}
