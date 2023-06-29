package io.github.arainko.ducktape

import scala.quoted.*
import io.github.arainko.ducktape.internal.modules.*
import scala.collection.IterableFactory
import scala.collection.Factory
import io.github.arainko.ducktape.internal.modules.*


object StructTransform {
  import Structure.*

  inline def transform[Source, Dest](value: Source): Dest = ${ createTransformation[Source, Dest]('value) }

  def createTransformation[Source: Type, Dest: Type](source: Expr[Source])(using Quotes) = {
    val src = Structure.of[Source]
    val dest = Structure.of[Dest]
    recurse[Source, Dest](src, dest, source)
  }

  private def recurse[A: Type, B: Type](source: Structure, dest: Structure, value: Expr[A])(using Quotes): Expr[B] = {
    import quotes.reflect.*

    (source.force -> dest.force) match {
      case (source, dest) if source.typeRepr <:< dest.typeRepr => 
        value.asExprOf[B]

      case Structure('[Option[srcTpe]], srcName) -> Structure('[Option[destTpe]], destName) => 
        val srcVal = value.asExprOf[Option[srcTpe]]
        '{ $srcVal.map(src => ${ createTransformation[srcTpe, destTpe]('src) }) }.asExprOf[B]

      case source -> Structure('[Option[destTpe]], destName) => 
        '{ Some( ${createTransformation[A, destTpe](value)} ) }.asExprOf[B]

      case Structure('[Iterable[srcTpe]], srcName) -> Structure('[Iterable[destTpe]], destName) =>
        val factory = Expr.summon[Factory[destTpe, B]].get
        val srcVal = value.asExprOf[Iterable[srcTpe]]
        '{ $srcVal.map(src => ${ createTransformation[srcTpe, destTpe]('src) }).to($factory) }

      case (source: Product, dest: Product) => 
        transformProduct[A, B](value, source, dest)

      case (source: Coproduct, dest: Coproduct) => 
        transformCoproducts[A, B](value, source, dest)
        
      case (source: Structure.Singleton, dest: Structure.Singleton) if source.name == dest.name => 
        dest.value.asExprOf[B]

      case (source: ValueClass, dest) if source.paramTpe.repr <:< dest.tpe.repr => 
        value.accessFieldByName(source.paramFieldName).asExprOf[B]

      case (source, dest: ValueClass) if source.tpe.repr <:< dest.paramTpe.repr => 
        Constructor(dest.tpe.repr)
          .appliedToArgs(value.asTerm :: Nil)
          .asExprOf[B]

      case (source: Ordinary, dest: Ordinary) => ???
      case other => ???
    }
  }

  private def transformProduct[A: Type, B: Type](value: Expr[A], source: Product, dest: Product)(using Quotes): Expr[B] = {
    import quotes.reflect.*

    val destTpe = TypeRepr.of(using dest.tpe)
    val constructor = Constructor(destTpe)

    val args = dest.fields.map { (destName, destStruct) => 
      val srcStruct= source.fields(destName)
      val fieldValue = (srcStruct.tpe -> destStruct.tpe) match {
        case '[src] -> '[dest] =>
          val accessed = value.accessFieldByName(destName).asExprOf[src]
          recurse[src, dest](srcStruct, destStruct, accessed)
      }
      NamedArg(destName, fieldValue.asTerm)
    }
    constructor.appliedToArgs(args.toList).asExprOf[B]
  }

  private def transformCoproducts[A: Type, B: Type](value: Expr[A], source: Coproduct, dest: Coproduct)(using Quotes): Expr[B] = {
    import quotes.reflect.*

    val branches = source.children.map { (sourceName, sourceStruct) => 
      val destStruct = dest.children(sourceName)
      (sourceStruct.tpe -> destStruct.tpe) match {
        case '[src] -> '[dest] =>
          val casted = '{ $value.asInstanceOf[src] }
          IfBranch(IsInstanceOf(value, summon[Type[src]]), recurse[src, dest](sourceStruct, destStruct, casted))
      }
    }
    ifStatement(branches.toList).asExprOf[B]
  }

  private def ifStatement(using Quotes)(branches: List[IfBranch]): quotes.reflect.Term = {
    import quotes.reflect.*

    branches match {
      case IfBranch(cond, value) :: xs =>
        If(cond.asTerm, value.asTerm, ifStatement(xs))
      case Nil =>
        '{ throw RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation") }.asTerm
    }
  }

  private def IsInstanceOf(value: Expr[Any], tpe: Type[?])(using Quotes) =
    tpe match {
      case '[tpe] => '{ $value.isInstanceOf[tpe] }
    }

  private case class IfBranch(cond: Expr[Boolean], value: Expr[Any])

}
