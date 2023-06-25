package io.github.arainko.ducktape

import scala.quoted.*
import io.github.arainko.ducktape.internal.modules.*
import scala.collection.IterableFactory
import scala.collection.Factory

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
      case (source, dest) if source.typeRepr <:< dest.typeRepr => value.asExprOf[B]
      case Structure('[Option[srcTpe]], srcName) -> Structure('[Option[destTpe]], destName) => 
        val srcVal = value.asExprOf[Option[srcTpe]]
        '{ $srcVal.map(src => ${ createTransformation[srcTpe, destTpe]('src) }) }.asExprOf[B]
      // case (source, dest) if 
      case source -> Structure('[Option[destTpe]], destName) => 
        '{ Some( ${createTransformation[A, destTpe](value)} ) }.asExprOf[B]
      case Structure('[Iterable[srcTpe]], srcName) -> Structure('[Iterable[destTpe]], destName) =>
        val factory = Expr.summon[Factory[destTpe, B]].get
        val srcVal = value.asExprOf[Iterable[srcTpe]]
        '{ $srcVal.map(src => ${ createTransformation[srcTpe, destTpe]('src) }).to($factory) }
      case (source: Product, dest: Product) => transformProduct[A, B](value, source, dest)
      case (source: Coproduct, dest: Coproduct) => ???
      case (source: Structure.Singleton, dest: Structure.Singleton) => ???
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

}
