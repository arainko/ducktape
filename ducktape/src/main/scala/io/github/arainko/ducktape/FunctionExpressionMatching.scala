package io.github.arainko.ducktape

import scala.quoted.*

object FunctionExpressionMatching {
  inline def printOutUsedMethods(inline func: FieldName => FieldName): Unit = ${ collectUsedMethods('func) }

  def collectUsedMethods(func: Expr[FieldName => FieldName])(using Quotes): Expr[Unit] = {
    import quotes.reflect.*

    def recurse(current: Expr[FieldName => FieldName], acc: List[String])(using Quotes): List[String] = {
      current match {
        // $body is the next tree with the '.lowercase' call stripped away
        case '{ (arg: FieldName) => ($body(arg): FieldName).lowercase } => recurse(body, "lowercase" :: acc)
        // $body is the next tree with the '.uppercase' call stripped away
        case '{ (arg: FieldName) => ($body(arg): FieldName).uppercase } => recurse(body, "uppercase" :: acc)
        // this matches an identity function, i.e. the end of our loop
        case '{ (arg: FieldName) => arg } => acc
        case _ => report.errorAndAbort("Not a simple lambda")
      }
    }

    val res = recurse(func, Nil)
    quotes.reflect.report.info(res.mkString(", "))
    '{}
  }

  "asad".toLowerCase.toUpperCase.strip.trim

  def matchIdentityFunction[A: Type](func: Expr[A => A])(using Quotes): Unit = 
    func match 
      case '{ (arg: A) => arg } => // this matches functions that just return their arguments, like (value: Int) => int
  
  def matchMethodCallChain(func: Expr[String => String])(using Quotes) = 
    func match 
      case '{ (arg: String) => arg.toLowerCase.toUpperCase.strip.trim } => // this matches functions that are made up of this particular method call chain
}
