package io.github.arainko.ducktape.total

import io.github.arainko.ducktape.*

class RenamerSuite extends DucktapeSuite {

  test("Renamer#toLowerCase works") {
    case class Source(field: Int)
    case class Dest(FIELD: Int)

    assertTransformsConfigured(
      Source(1),
      Dest(1)
    )(
      Field.modifyDestNames(_.toLowerCase)
    )
  }

  test("Renamer#toUpperCase works") {
    case class Source(FIELD: Int)
    case class Dest(field: Int)

    assertTransformsConfigured(
      Source(1),
      Dest(1)
    )(
      Field.modifyDestNames(_.toUpperCase)
    )
  }

  test("Renamer#rename works") {
    case class Source(FIELD: Int)
    case class Dest(field: Int)

    assertTransformsConfigured(
      Source(1),
      Dest(1)
    )(
      Field.modifyDestNames(_.rename("field", "FIELD"))
    )
  }

  test("Renamer#replace works") {
    case class Source(f1i1e1l1d: Int)
    case class Dest(f_i_e_l_d: Int)

    assertTransformsConfigured(
      Source(1),
      Dest(1)
    )(
      Field.modifyDestNames(_.replace("_", "1"))
    )
  }

  test("Renamer#regexReplace works") {
    case class Source(f1i2e3l4d: Int)
    case class Dest(f_1_i_2_e_3_l_4_d: Int)

    assertTransformsConfigured(
      Source(1),
      Dest(1)
    )(
      Field.modifyDestNames(_.regexReplace("""_(\d)_""", "$1"))
    )
  }

  test("Renamer#stripPrefix works") {
    case class Source(field: Int)
    case class Dest(PREFIX_field: Int)

    assertTransformsConfigured(
      Source(1),
      Dest(1)
    )(
      Field.modifyDestNames(_.stripPrefix("PREFIX_"))
    )
  }

  test("Renamer#stripSuffix works") {
    case class Source(field: Int)
    case class Dest(field_SUFFIX: Int)

    assertTransformsConfigured(
      Source(1),
      Dest(1)
    )(
      Field.modifyDestNames(_.stripSuffix("_SUFFIX"))
    )
  }

  test("Renamer#capitalize works") {
    case class Source(field: Int)
    case class Dest(Field: Int)

    assertTransformsConfigured(
      Source(1),
      Dest(1)
    )(
      Field.modifySourceNames(_.capitalize)
    )
  }

  test("Renamer functions are sequenced in the right order") {
    case class Source(field: Int)
    case class Dest(FIELD_a_b_c: Int)

    assertTransformsConfigured(
      Source(1),
      Dest(1)
    )(
      Field.modifyDestNames(
        _.toUpperCase.replace("_A", "").toLowerCase.replace("_b", "").toUpperCase.replace("_C", "").toLowerCase
      )
    )
  }
}
