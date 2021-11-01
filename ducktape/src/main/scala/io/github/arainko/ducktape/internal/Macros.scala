package io.github.arainko.ducktape

import scala.quoted.*

private[ducktape] object Macros:

  inline def showType[T] = ${ showTypeImpl[T] }

  inline def verifyFieldExists[Label <: String, Subcases <: Tuple, Tpe] =
    ${ verifyFieldExistsImpl[Label, Subcases, Tpe] }

  inline def verifyTypes[Provided, Expected] = ${ verifyTypesImpl[Provided, Expected] }

  def verifyFieldExistsImpl[Label <: String: Type, Subcases <: Tuple: Type, Tpe: Type](using Quotes) = {
    import quotes.reflect.*

    val fieldName = Type.valueOfConstant[Label].get
    val nothing = TypeRepr.of[Nothing]
    val fieldType = TypeRepr.of[Field.TypeForLabel[Label, Subcases]].simplified
    val tpe = TypeRepr.of[Tpe]

    if fieldType =:= nothing then report.errorAndAbort(s"Field '$fieldName' was not found in ${tpe.show}")
    else '{}
  }

  def verifyTypesImpl[Provided: Type, Expected: Type](using Quotes) = {
    import quotes.reflect.*
    given Printer[TypeRepr] = Printer.TypeReprShortCode

    val provided = TypeRepr.of[Provided].simplified
    val expected = TypeRepr.of[Expected].simplified

    if provided <:< expected then '{}
    else
      report.errorAndAbort {
        s"""Type mismatch!
        |Expected: ${expected.show}  
        |but got: ${provided.show}""".stripMargin
      }
  }

  def showTypeImpl[T: Type](using Quotes): Expr[String] = {
    import quotes.reflect.*
    Expr(Type.show[T])
  }

end Macros
