package io.github.arainko.ducktape

sealed trait Selector {
  extension [A](self: A) def at[B <: A]: B

  extension [Elem](self: Iterable[Elem] | Option[Elem]) def element: Elem
}

object Selector {
  def whatever[Coll[a] <: Iterable[a], Elem](sel: Selector ?=> Coll[Elem] => Elem): Elem = ???

  case class A(map: Map[Int, Int])
  case class B(map: Map[Int, String])

  val a = 
    internal.CodePrinter.code:
      A(Map.empty).into[B].transform(
        // Field.const(_.map.element._2, "")
      )

  // summon[Map[Int, Int] <:< Iterable[(Int, Int)]]

  // internal.CodePrinter.structure:
  //   whatever[Map, (Int, Int)]

  
}
