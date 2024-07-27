package io.github.arainko.ducktape.issues
import io.github.arainko.ducktape.*

class Issue187Suite extends DucktapeSuite {
  test("minimization works") {
    
    given Transformer.Fallible[Option, Int, String] = a => Some(a.toString)

    case class Source(int: Int)
    case class Dest(int: Option[String])

    Mode.FailFast.option.locally {
      Source(1).fallibleTo[Dest]
    }
  }
}
