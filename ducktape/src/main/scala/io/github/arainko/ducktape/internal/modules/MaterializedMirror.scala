package io.github.arainko.ducktape.internal.modules

import scala.annotation.tailrec
import scala.deriving.Mirror
import scala.quoted.*

private[ducktape] final class MaterializedMirror(
  val mirroredElemTypes: List[Type[?]],
  val mirroredElemLabels: List[String]
)

private[ducktape] object MaterializedMirror {

  def create[A: Type](mirror: Expr[Mirror.Of[A]])(using Quotes): MaterializedMirror =
    mirror match {
      case '{
            $m: Mirror.Of[A] {
              type MirroredElemTypes = types
              type MirroredElemLabels = labels
            }
          } =>
        import quotes.reflect.*
        val elemTypes = tupleTypeElements(TypeRepr.of[types])
        val labels = tupleTypeElements(TypeRepr.of[labels]).map {
          case '[IsString[tpe]] => Type.valueOfConstant[tpe].getOrElse(report.errorAndAbort("Couldn't extract constact value"))
        }
        MaterializedMirror(elemTypes, labels)
    }

  private def tupleTypeElements(using Quotes)(tp: quotes.reflect.TypeRepr): List[Type[?]] = {
    import quotes.reflect.*

    @tailrec def loop(curr: TypeRepr, acc: List[Type[?]]): List[Type[?]] =
      curr match {
        case AppliedType(pairTpe, head :: tail :: Nil) => loop(tail, head.asType :: acc)
        case _                                         => acc
      }
    loop(tp, Nil).reverse
  }

  private type IsString[A <: String] = A

}
