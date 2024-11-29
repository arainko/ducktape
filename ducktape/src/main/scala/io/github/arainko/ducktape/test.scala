package io.github.arainko.ducktape

import scala.compiletime.ops.any

case class Source(INT: Int)

case class Dest(int: Int)

/* 
Structure -> INT, int

Planner -> TO_LOWER(INT) -> int, Plan(src = INT, dest = int)

Interpreter -> 

 */

object test {
  // Transformer.Debug.showCode:
    // Source(1).to[Dest]

    
  
}
