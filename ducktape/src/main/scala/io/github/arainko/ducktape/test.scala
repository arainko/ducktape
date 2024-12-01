package io.github.arainko.ducktape


case class Source(INT: Int, str: String)

case class Dest(int: Int)

/* 
Structure -> INT, int

Planner -> TO_LOWER(INT) -> int, Plan(src = INT, dest = int)

Interpreter -> 


Field.transformFields(FieldName.toUpperCase ~ FieldName.)

 */

object test {

  Transformer.Debug.showCode:
    Source(1, "a").to[Dest]

    
  
}
