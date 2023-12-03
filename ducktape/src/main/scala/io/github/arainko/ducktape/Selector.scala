package io.github.arainko.ducktape

sealed trait Selector {
  extension [A](self: A) def at[B <: A]: B

  extension [Coll[a] <: (Iterable[a] | Option[a]), Elem](self: Coll[Elem]) def element: Elem
}

object Selector {
  def whatever[A](sel: Selector ?=> Option[A] => A): A = sel(using ???).apply(None)

  internal.CodePrinter.structure:
    whatever[Int](_.element)

  
}
