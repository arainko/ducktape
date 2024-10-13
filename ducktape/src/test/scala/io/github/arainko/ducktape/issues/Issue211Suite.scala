package io.github.arainko.ducktape.issues

import io.github.arainko.ducktape.*

class Issue211Suite extends DucktapeSuite {
  test("directly configuring a Dest case doesn't fail at runtime") {
    enum SourceLevel3 {
      case One(int: Int)
      case Two(str: String)
    }

    enum DestLevel3 {
      case One(int: Long)
      case Two(str: String)
    }

    val source = SourceLevel3.One(1)
    val expected = DestLevel3.One(6)

    assertTransformsConfigured(source, expected)(
      Field.const(_.at[DestLevel3.One], DestLevel3.One(6L))
    )
  }
}
