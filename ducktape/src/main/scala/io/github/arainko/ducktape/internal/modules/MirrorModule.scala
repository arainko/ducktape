package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import scala.annotation.tailrec

// Taken from shapeless 3:
// https://github.com/typelevel/shapeless-3/blob/main/modules/deriving/src/main/scala/shapeless3/deriving/internals/reflectionutils.scala
private[internal] trait MirrorModule { self: Module =>
  import quotes.reflect.*

  case class Mirror(
    mirroredType: TypeRepr,
    mirroredMonoType: TypeRepr,
    mirroredElemTypes: List[TypeRepr],
    mirroredLabel: String,
    mirroredElemLabels: List[String]
  )

  object Mirror {
    def apply(mirror: Expr[deriving.Mirror]): Option[Mirror] = {
      val mirrorTpe = mirror.asTerm.tpe.widen
      for {
        mirroredType <- findMemberType(mirrorTpe, "MirroredType")
        mirroredMonoType <- findMemberType(mirrorTpe, "MirroredMonoType")
        mirroredElemTypes <- findMemberType(mirrorTpe, "MirroredElemTypes")
        mirroredLabel <- findMemberType(mirrorTpe, "MirroredLabel")
        mirroredElemLabels <- findMemberType(mirrorTpe, "MirroredElemLabels")
      } yield {
        val elemTypes = tupleTypeElements(mirroredElemTypes)
        val ConstantType(StringConstant(label)) = mirroredLabel
        val elemLabels = tupleTypeElements(mirroredElemLabels).map { case ConstantType(StringConstant(l)) => l }
        Mirror(mirroredType, mirroredMonoType, elemTypes, label, elemLabels)
      }
    }
  }

  private def tupleTypeElements(tp: TypeRepr): List[TypeRepr] = {
    @tailrec def loop(tp: TypeRepr, acc: List[TypeRepr]): List[TypeRepr] = tp match {
      case AppliedType(pairTpe, List(hd: TypeRepr, tl: TypeRepr)) => loop(tl, hd :: acc)
      case _                                                      => acc
    }
    loop(tp, Nil).reverse
  }

  private def low(tp: TypeRepr): TypeRepr = tp match {
    case tp: TypeBounds => tp.low
    case tp             => tp
  }

  private def findMemberType(tp: TypeRepr, name: String): Option[TypeRepr] = tp match {
    case Refinement(_, `name`, tp) => Some(low(tp))
    case Refinement(parent, _, _)  => findMemberType(parent, name)
    case AndType(left, right)      => findMemberType(left, name).orElse(findMemberType(right, name))
    case _                         => None
  }
}
