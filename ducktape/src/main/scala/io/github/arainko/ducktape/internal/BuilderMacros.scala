package io.github.arainko.ducktape.internal

import scala.quoted.*
import io.github.arainko.ducktape.Field
import scala.deriving.Mirror


object BuilderMacros {
  transparent inline def droppedWithLambda[Fields <: Tuple] =
    ${ droppedWithLambdaImpl[Fields] }

  def droppedWithLambdaImpl[Fields <: Tuple: Type](using Quotes) = 
    BuilderMacros().fromInlinedLambda[Fields] 
}

class BuilderMacros(using Quotes) {
  import quotes.reflect.*

  def fromInlinedLambda[Fields <: Tuple: Type] = {
    val cons = ConstantType(StringConstant("costam")).asType
    val droppedType = cons match {
      case '[field] => Type.of[Field.DropByLabel[field, Fields]]
    }
    droppedType match {
      case '[dropped] => '{ ??? : dropped }
    }
  }
}
