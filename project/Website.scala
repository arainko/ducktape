import laika.ast.Image
import laika.ast.LengthUnit.px
import laika.ast.Span
import laika.ast.TemplateString
import laika.helium.Helium
import laika.helium.config._
import laika.theme.config.Color
import org.typelevel.sbt.TypelevelGitHubPlugin.gitHubUserRepo
import sbt.Def._
import sbt.Keys.licenses
import org.typelevel.sbt.site.*
import org.typelevel.sbt.TypelevelSitePlugin.autoImport.*
import laika.ast.*
import laika.ast.Path.Root
// import org.typel

object DeffSiteSettings {

  val defaultHomeLink: ThemeLink = ImageLink.external(
    "https://github.com/arainko/ducktape/tree/series/0.2.x",
    Image.external(
      "https://user-images.githubusercontent.com/46346508/236060869-3b118075-f660-44c9-9d0d-d40fba5c8db0.svg",
      height = Some(Length(50, px)),
      width = Some(Length(50, px))
    )
  )

  val favIcon: Favicon =
    Favicon.external(
      "https://user-images.githubusercontent.com/46346508/236060869-3b118075-f660-44c9-9d0d-d40fba5c8db0.svg",
      "32x32",
      "image/svg+xml"
    )

  // light theme colours
  // Tl suffix indicates these are lifted directly from somewhere within the Typelevel site
  // val redTl = Color.hex("f8293a")
  val brightRedTl = Color.hex("fe4559")
  val coralTl = Color.hex("f86971")
  val pinkTl = Color.hex("ffb4b5")
  val whiteTl = Color.hex("ffffff")
  val gunmetalTl = Color.hex("35213f")
  val platinumTl = Color.hex("e6e8ea")
  // Extra colours to supplement
  val lightPink = Color.hex("ffe7e7")
  val slateBlue = Color.hex("385a70") // 406881 (original slateCyan)
  val mediumSlateCyanButDarker = Color.hex("8ebac7")
  val mediumSlateCyan = Color.hex("b0cfd8")
  val lightSlateCyan = Color.hex("ddeaed")
  val lighterSlateCyan = Color.hex("f4f8fa")
  val softYellow = Color.hex("f9f5d9")

  val logoDark = Color.hex("b4a7d6")
  val logoLight = Color.hex("d9d2e9")
  val veryLight = Color.hex("f4f2f7")
  val secondary = Color.hex("8f7eb9")

  val defaults = setting {
    TypelevelSiteSettings.defaults.value.site
      .footer()
      .site
      .topNavigationBar(homeLink = defaultHomeLink)
      .site
      .favIcons(favIcon)
      .site
      .pageNavigation(enabled = true)
      .site
      .internalCSS(Root / "styles")
      .site
      .layout(contentWidth = Length(100, LengthUnit.percent))
      .site
      .themeColors(
        primary = logoDark,
        secondary = secondary,
        primaryMedium = logoLight,
        primaryLight = veryLight,
        text = gunmetalTl,
        background = whiteTl,
        bgGradient = (mediumSlateCyan, lighterSlateCyan)
      )
      .site
      .messageColors(
        info = slateBlue,
        infoLight = lightSlateCyan,
        warning = slateBlue,
        warningLight = softYellow,
        error = slateBlue,
        errorLight = lightPink
      )
      .site
      .syntaxHighlightingColors(
        base = ColorQuintet(
          gunmetalTl,
          Color.hex("73ad9b"), // comments
          Color.hex("b2adb4"), // ?
          pinkTl, // identifier
          platinumTl // base colour
        ),
        wheel = ColorQuintet(
          Color.hex("8fa1c9"), // substitution, annotation
          Color.hex("81e67b"), // keyword, escape-sequence
          Color.hex("ffde6d"), // declaration name
          Color.hex("86aac1"), // literals
          coralTl // type/class name
        )
      )

  }
}
