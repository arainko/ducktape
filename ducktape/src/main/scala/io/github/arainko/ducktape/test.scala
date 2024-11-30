package io.github.arainko.ducktape

import java.util.Locale


case class Source(INT: Int, in_t: Int)

case class Dest(int: Int)

/* 
Structure -> INT, int

Planner -> TO_LOWER(INT) -> int, Plan(src = INT, dest = int)

Interpreter -> 


Field.transformFields(FieldName.toUpperCase ~ FieldName.)

 */

object test {
  inline given (String => String) = _.toUpperCase().toLowerCase()

  val d: Locale.CANADA.type = ???

  inline def use(using inline a: String => String) = 
    Transformer.Debug.showCode(a)

  use

  Transformer.Debug.showCode:
    Source(1, 2).to[Dest]

    
  
}
