package io.github.arainko.ducktape.internal.modules

import scala.quoted.*

private[ducktape] object Constructor {
  def apply(using Quotes)(tpe: quotes.reflect.TypeRepr): quotes.reflect.Term = {
    import quotes.reflect.*

    val (repr, constructor, tpeArgs) =
      tpe match {
        case AppliedType(repr, reprArguments) => (repr, repr.typeSymbol.primaryConstructor, reprArguments)
        case notApplied                       => (tpe, tpe.typeSymbol.primaryConstructor, Nil)
      }

    //workaround for invoking constructors of singleton which in turn actually create new instances of singletons!
    if (tpe.typeSymbol.flags.is(Flags.Module)) report.errorAndAbort("Cannot invoke constructor of a singleton")

    New(Inferred(repr))
      .select(constructor)
      .appliedToTypes(tpeArgs)
  }

  def construct[A: Type](fields: List[Field.Unwrapped])(using Quotes): Expr[A] = {
    import quotes.reflect.*

    Constructor(TypeRepr.of[A])
      .appliedToArgs(fields.map(field => NamedArg(field.underlying.name, field.value.asTerm)))
      .asExprOf[A]
  }
}
