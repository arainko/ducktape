package io.github.arainko.ducktape.internal.modules

import scala.annotation.tailrec
import scala.deriving.Mirror
import scala.quoted.*

private[ducktape] final class MaterializedMirror[Q <: Quotes & Singleton] private (using val quotes: Q)(
  val mirroredType: quotes.reflect.TypeRepr,
  val mirroredElemTypes: List[quotes.reflect.TypeRepr],
  val mirroredElemLabels: List[String]
)

private[ducktape] object MaterializedMirror {

  def createOrAbort[A: Type](mirror: Expr[Mirror.Of[A]])(using Quotes): MaterializedMirror[quotes.type] =
    mirror match {
      case '{
            $m: Mirror.Of[A] {
              type MirroredElemTypes = types
              type MirroredElemLabels = labels
            }
          } =>
        import quotes.reflect.*
        val elemTypes = tupleTypeElements(TypeRepr.of[types])
        val labels = tupleTypeElements(TypeRepr.of[labels]).map { case ConstantType(StringConstant(l)) => l }
        MaterializedMirror(TypeRepr.of[A], elemTypes, labels)
    }

  private def tupleTypeElements(using Quotes)(tp: quotes.reflect.TypeRepr): List[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*

    @tailrec def loop(curr: TypeRepr, acc: List[TypeRepr]): List[TypeRepr] =
      curr match {
        case AppliedType(pairTpe, head :: tail :: Nil) => loop(tail, head :: acc)
        case _                                         => acc
      }
    loop(tp, Nil).reverse
  }

}
