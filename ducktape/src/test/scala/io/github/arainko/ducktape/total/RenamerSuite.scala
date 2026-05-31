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

  test("camelCase to snake_case transformation") {
    case class Camel(
      simpleField: Int,
      fieldWithNumber1: Int,
      field2WithNumber: Int,
      _leadingUnderscore: Int,
      trailingUnderscore_ : Int,
      mixedCASEField: Int,
      fieldWith123Numbers: Int,
      fieldWithMultiple___Underscores: Int
    )

    case class Snake(
      simple_field: Int,
      field_with_number1: Int,
      field2_with_number: Int,
      _leading_underscore: Int,
      trailing_underscore_ : Int,
      mixed_case_field: Int,
      field_with123_numbers: Int,
      field_with_multiple___underscores: Int
    )

    val camel = Camel(
      simpleField = 1,
      fieldWithNumber1 = 2,
      field2WithNumber = 3,
      _leadingUnderscore = 4,
      trailingUnderscore_ = 5,
      mixedCASEField = 6,
      fieldWith123Numbers = 7,
      fieldWithMultiple___Underscores = 8
    )
    val snake = Snake(
      simple_field = 1,
      field_with_number1 = 2,
      field2_with_number = 3,
      _leading_underscore = 4,
      trailing_underscore_ = 5,
      mixed_case_field = 6,
      field_with123_numbers = 7,
      field_with_multiple___underscores = 8
    )

    assertTransformsConfigured(camel, snake)(Field.modifySourceNames(Renamer.camelCase.toSnakeCase))
  }

  test("snake_case to camelCase transformation") {
    case class Snake(
      simple_field: Int,
      field_with_number1: Int,
      field2_with_number: Int,
      _leading_underscore: Int,
      trailing_underscore_ : Int,
      mixed_case_field: Int,
      field_with123_numbers: Int,
      field_with_multiple___underscores: Int
    )

    case class Camel(
      simpleField: Int,
      fieldWithNumber1: Int,
      field2WithNumber: Int,
      leadingUnderscore: Int,
      trailingUnderscore_ : Int,
      mixedCaseField: Int,
      fieldWith123Numbers: Int,
      fieldWithMultiple__Underscores: Int
    )

    val snake = Snake(
      simple_field = 1,
      field_with_number1 = 2,
      field2_with_number = 3,
      _leading_underscore = 4,
      trailing_underscore_ = 5,
      mixed_case_field = 6,
      field_with123_numbers = 7,
      field_with_multiple___underscores = 8
    )

    val expectedCamel = Camel(
      simpleField = 1,
      fieldWithNumber1 = 2,
      field2WithNumber = 3,
      leadingUnderscore = 4,
      trailingUnderscore_ = 5,
      mixedCaseField = 6,
      fieldWith123Numbers = 7,
      fieldWithMultiple__Underscores = 8
    )
    assertTransformsConfigured(snake, expectedCamel)(Field.modifySourceNames(Renamer.snakeCase.toCamelCase))
  }

  test("camelCase to kebab-case transformation") {
    case class Camel(
      simpleField: Int,
      fieldWithNumber1: Int,
      field2WithNumber: Int,
      `-leadingDash`: Int,
      `trailingDash-`: Int,
      mixedCASEField: Int,
      fieldWith123Numbers: Int,
      `fieldWithMultiple---Dashes`: Int,
      fieldWithDash1: Int
    )

    case class Kebab(
      `simple-field`: Int,
      `field-with-number1`: Int,
      `field2-with-number`: Int,
      `-leading-dash`: Int,
      `trailing-dash-`: Int,
      `mixed-case-field`: Int,
      `field-with123-numbers`: Int,
      `field-with-multiple---dashes`: Int,
      `field-with-dash1`: Int
    )

    val camel = Camel(
      simpleField = 1,
      fieldWithNumber1 = 2,
      field2WithNumber = 3,
      `-leadingDash` = 4,
      `trailingDash-` = 5,
      mixedCASEField = 6,
      fieldWith123Numbers = 7,
      `fieldWithMultiple---Dashes` = 8,
      fieldWithDash1 = 9
    )

    val expectedKebab = Kebab(
      `simple-field` = 1,
      `field-with-number1` = 2,
      `field2-with-number` = 3,
      `-leading-dash` = 4,
      `trailing-dash-` = 5,
      `mixed-case-field` = 6,
      `field-with123-numbers` = 7,
      `field-with-multiple---dashes` = 8,
      `field-with-dash1` = 9
    )

    assertTransformsConfigured(camel, expectedKebab)(Field.modifySourceNames(Renamer.camelCase.toKebabCase))
  }

  test("kebab-case to camelCase transformation") {
    case class Kebab(
      `simple-field`: Int,
      `field-With-number1`: Int,
      `field2-with-number`: Int,
      `-leading-dash`: Int,
      `trailing-dash-`: Int,
      `mixed-case-field`: Int,
      `field-with123-numbers`: Int,
      `field-with-multiple---dashes`: Int,
      `field-with-dash1`: Int,
      `-Uppercase-leading-dash`: Int
    )
    case class Camel(
      simpleField: Int,
      fieldWithNumber1: Int,
      field2WithNumber: Int,
      leadingDash: Int,
      `trailingDash-`: Int,
      mixedCaseField: Int,
      fieldWith123Numbers: Int,
      `fieldWithMultiple--Dashes`: Int,
      fieldWithDash1: Int,
      uppercaseLeadingDash: Int
    )

    val kebab = Kebab(
      `simple-field` = 1,
      `field-With-number1` = 2,
      `field2-with-number` = 3,
      `-leading-dash` = 4,
      `trailing-dash-` = 5,
      `mixed-case-field` = 6,
      `field-with123-numbers` = 7,
      `field-with-multiple---dashes` = 8,
      `field-with-dash1` = 9,
      `-Uppercase-leading-dash` = 10
    )
    val expectedCamel = Camel(
      simpleField = 1,
      fieldWithNumber1 = 2,
      field2WithNumber = 3,
      leadingDash = 4,
      `trailingDash-` = 5,
      mixedCaseField = 6,
      fieldWith123Numbers = 7,
      `fieldWithMultiple--Dashes` = 8,
      fieldWithDash1 = 9,
      uppercaseLeadingDash = 10
    )

    assertTransformsConfigured(kebab, expectedCamel)(Field.modifySourceNames(Renamer.kebabCase.toCamelCase))
  }
}
