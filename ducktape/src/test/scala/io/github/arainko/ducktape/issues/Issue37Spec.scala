package io.github.arainko.ducktape.issues

import io.github.arainko.ducktape.*

// https://github.com/arainko/ducktape/issues/37
class Issue37Spec extends DucktapeSuite { 
  final case class Rec[A](value: A, rec: Option[Rec[A]])

  given rec[A,B](using Transformer[A, B]): Transformer[Rec[A], Rec[B]] = Transformer.define[Rec[A], Rec[B]].build()

  rec[Int, Option[Int]] // Failed to fetch the wrapped field name of scala.Int
}
