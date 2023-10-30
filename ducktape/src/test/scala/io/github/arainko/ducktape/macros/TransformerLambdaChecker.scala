// package io.github.arainko.ducktape.macros

// import io.github.arainko.ducktape.*
// import io.github.arainko.ducktape.internal.modules.TransformerLambda
// import io.github.arainko.ducktape.internal.modules.TransformerLambda.*

// import scala.quoted.*

// object TransformerLambdaChecker {
//   inline def check[A](inline transformer: Transformer[?, ?]): Unit = ${ checkMacro[A]('transformer) }

//   def checkMacro[A: Type](expr: Expr[Transformer[?, ?]])(using Quotes): Expr[Unit] = {
//     import quotes.reflect.*

//     TransformerLambda
//       .fromTransformer(expr)
//       .map {
//         case _: ForProduct[?] => checkType[A, Transformer.ForProduct.type]
//         case _: FromAnyVal[?] => checkType[A, Transformer.FromAnyVal.type]
//         case _: ToAnyVal[?]   => checkType[A, Transformer.ToAnyVal.type]
//       }
//       .getOrElse(report.errorAndAbort(s"Not matched: ${Printer.TreeStructure.show(expr.asTerm)}"))
//   }

//   private def checkType[Actual: Type, Expected: Type](using Quotes) = {
//     import quotes.reflect.*
//     val actual = TypeRepr.of[Actual]
//     val expected = TypeRepr.of[Expected]
//     if (actual =:= expected) '{ () }
//     else report.errorAndAbort(s"${Type.show[Actual]} != ${Type.show[Expected]}")
//   }
// }
