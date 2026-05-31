package io.github.arainko.ducktape

sealed trait Selector {

  extension [A](self: A) {

    /**
     * 'Zooms-in' on a subtype of an enum or a sealed trait
     *
     * {{{
     * enum Source {
     *   case One, Two, Three
     * }
     *
     * enum Dest {
     *   case One, Two
     * }
     *
     * val source: Source = Source.Three
     *
     * source
     *   .into[Dest]
     *   .transform(Case.const(_.at[Source.Three.type], Dest.Two))
     * // Dest.Two
     * }}}
     */
    def at[B <: A]: B
  }

  extension [Elem](self: Iterable[Elem] | Option[Elem]) {

    /**
     * 'Drills through' an Option or an Iterable (List, Vector, Map etc.)
     * 
     * {{{
     * case class Source(opt: Option[Int])
     * 
     * case class Dest(randomField: Option[String])
     * 
     * val source = Source(Some(1))
     * 
     * source
     *  .into[Dest]
     *  .transform(Field.const(_.randomField.element, "CONST!"))
     * // Dest(Some("CONST!"))
     * }}}
     */
    def element: Elem
  }

  /**
   * 'Drills through' a value wrapped in a fallible wrapper (depending on which Mode is currently in scope)
   */
  extension [Elem, F[+x]](using Mode[F])(self: F[Elem]) def element: Elem
}
