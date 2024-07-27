package io.github.arainko.ducktape.issues

import io.github.arainko.ducktape.*

class Issue190Suite extends DucktapeSuite {
  
  test("transforming from tuples concatenated together works") {
    case class Big(int1: Int, int2: Int, int3: Int, int4: Int, int5: Int, int6: Int, int7: Int, int8: Int)

    val one = (1, 2, 3, 4)
    val two = (5, 6, 7, 8)

    val joined = (one ++ two)

    val expected = Big(1, 2, 3, 4, 5, 6, 7, 8)
    assertTransforms(joined, expected)
  }

}
