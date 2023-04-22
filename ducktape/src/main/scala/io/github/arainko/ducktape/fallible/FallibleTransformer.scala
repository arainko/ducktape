package io.github.arainko.ducktape.fallible

sealed trait FallibleTransformer[F[+x], Source, Dest] {
  def transform(value: Source): F[Dest]
}

object FallibleTransformer {
  trait Accumulating[F[+x], Source, Dest] extends FallibleTransformer[F, Source, Dest]
  object Accumulating extends instances.AccumulatingInstances {
    type Support[F[+x]] = Mode.Accumulating[F]
  }

  trait FailFast[F[+x], Source, Dest] extends FallibleTransformer[F, Source, Dest]
  object FailFast extends instances.FailFastInstances {
    type Support[F[+x]] = Mode.FailFast[F]
  }
}
