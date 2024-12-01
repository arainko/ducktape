package io.github.arainko.ducktape


case class Source(INT: Int, int: Int, str: String, STR: String)

case class Dest(str: String, int: Int)

/* 
Structure -> INT, int

Planner -> TO_LOWER(INT) -> int, Plan(src = INT, dest = int)

Interpreter -> 


Field.transformFields(FieldName.toUpperCase ~ FieldName.)

 */

object test {

  // Transformer.Debug.showCode:
    Source(1, 2, "a", "b")
      .into[Dest]
      .transform(
        Field.const(_.str, "")
      )

    
  
}
