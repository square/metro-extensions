package com.squareup.test.app

import kotlin.metadata.ExperimentalAnnotationsInMetadata
import kotlin.metadata.KmAnnotation
import kotlin.metadata.KmAnnotationArgument.KClassValue
import kotlin.metadata.KmClass
import kotlin.metadata.KmClassifier
import kotlin.metadata.KmFunction
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Reads the metadata of the separately compiled library, rather than JVM method annotations. */
@OptIn(ExperimentalAnnotationsInMetadata::class)
class GeneratedMetadataTest {
  @Test
  fun `binds methods and scope arguments are serialized without redundant mirrors`() {
    for (type in listOf("LibScoped", "ConstructorScoped", "InjectedScoped", "OtherScoped")) {
      val scope =
        when (type) {
          "LibScoped" -> "dev/zacsweers/metro/AppScope"
          "OtherScoped" -> "com/squareup/test/lib/OtherScope"
          else -> "com/squareup/test/lib/BootstrapScope"
        }
      val className =
        "com.squareup.test.lib.${type}MultibindingScopedContributions\$MultibindingScopedContribution"
      val metadata = metadata(className)
      val binds = metadata.functions.single { it.name == "binds$type" }
      assertEquals(KmClassifier.Class("mortar/Scoped"), binds.returnType.classifier)
      assertEquals(
        KmClassifier.Class("com/squareup/test/lib/$type"),
        binds.valueParameters.single().type.classifier,
      )
      assertTrue(binds.annotations.has("dev/zacsweers/metro/Binds"))
      assertTrue(binds.annotations.has("dev/zacsweers/metro/IntoSet"))
      assertEquals(
        KClassValue(scope),
        binds.annotations
          .single { it.className == "dev/zacsweers/metro/ForScope" }
          .arguments["scope"],
      )
      // Assert omission only for these public bindings, not for mirrors Metro still requires.
      assertFalse(metadata.nestedClasses.contains("BindsMirror"))
      assertFalse(Class.forName(className).declaredClasses.any { it.simpleName == "BindsMirror" })
    }
  }

  @Test
  fun `constructor providers and robot accessors are serialized exactly once`() {
    val scoped =
      function(
        "ConstructorScopedMultibindingScopedContributions\$Companion",
        "provideContributedMultibindingScoped",
      )
    assertTrue(scoped.annotations.has("dev/zacsweers/metro/Provides"))
    assertEquals(
      KmClassifier.Class("com/squareup/test/lib/ScopedDependency"),
      scoped.valueParameters.single().type.classifier,
    )
    val robot = metadata("com.squareup.test.lib.ConstructorRobot\$RobotContribution")
    assertEquals(
      setOf(
        "providecom_ssquareup_stest_slib_sConstructorRobotComponent",
        "getcom_squareup_test_lib_ConstructorRobotComponent",
      ),
      robot.functions.map { it.name }.toSet(),
    )
    assertEquals(2, robot.functions.size)
    val provider =
      robot.functions.single {
        it.name == "providecom_ssquareup_stest_slib_sConstructorRobotComponent"
      }
    assertTrue(provider.annotations.has("dev/zacsweers/metro/Provides"))
    assertEquals(
      KClassValue("com/squareup/test/lib/BootstrapScope"),
      provider.annotations
        .single { it.className == "dev/zacsweers/metro/SingleIn" }
        .arguments["scope"],
    )
    val injected = metadata("com.squareup.test.lib.LibRobot\$RobotContribution")
    assertEquals(
      listOf("getcom_squareup_test_lib_LibRobotComponent"),
      injected.functions.map { it.name },
    )
  }

  @Test
  fun `same named robots serialize distinct constructor providers`() {
    val providerNames = mutableSetOf<String>()
    for (feature in listOf("featurea", "featureb")) {
      val robot = metadata("com.example.$feature.SettingsRobot\$RobotContribution")
      val provider = robot.functions.single { it.name.startsWith("provide") }
      assertTrue(providerNames.add(provider.name))
      assertTrue(provider.annotations.has("dev/zacsweers/metro/Provides"))
      assertEquals(
        KmClassifier.Class("com/example/$feature/SettingsRobot"),
        provider.returnType.classifier,
      )
      assertEquals(
        KmClassifier.Class("kotlin/String"),
        provider.valueParameters.single().type.classifier,
      )
      assertEquals(
        listOf("getcom_example_${feature}_SettingsRobotComponent"),
        robot.functions.filterNot { it.name.startsWith("provide") }.map { it.name },
      )
    }
  }

  @Test
  fun `real and fake providers preserve function and parameter qualifiers`() {
    val real = function("BootstrapApi\$ServiceContribution", "provideBootstrapApi")
    assertTrue(real.annotations.has("dev/zacsweers/metro/Provides"))
    assertTrue(
      real.valueParameters
        .single { it.name == "serviceCreator" }
        .annotations
        .has("com/squareup/api/RetrofitAuthenticated")
    )
    assertTrue(
      real.valueParameters
        .single { it.name == "isFakeMode" }
        .annotations
        .has("com/squareup/development/FakeMode")
    )
    val qualifiedReal = function("FakeBootstrapApi\$ServiceContribution", "provideRealBootstrapApi")
    assertTrue(qualifiedReal.annotations.has("com/squareup/api/RealService"))
    assertEquals(
      KClassValue("com/squareup/test/lib/BootstrapScope"),
      qualifiedReal.annotations
        .single { it.className == "dev/zacsweers/metro/SingleIn" }
        .arguments["scope"],
    )
    val switcher = function("FakeBootstrapApi\$ServiceContribution", "provideBootstrapApi")
    assertTrue(
      switcher.valueParameters
        .single { it.name == "realService" }
        .annotations
        .has("com/squareup/api/RealService")
    )
    assertTrue(
      switcher.valueParameters
        .single { it.name == "isFakeMode" }
        .annotations
        .has("com/squareup/development/FakeMode")
    )
    val constructor =
      function("FakeBootstrapApi\$ServiceContribution", "provideContributedServiceReplacement")
    assertTrue(constructor.annotations.has("dev/zacsweers/metro/Provides"))
    assertEquals(
      KmClassifier.Class("com/squareup/test/lib/ScopedDependency"),
      constructor.valueParameters.single().type.classifier,
    )
  }

  @Test
  fun `both flag annotations serialize their set providers`() {
    for (type in listOf("LibFeatureFlag", "LibDynamicFlag")) {
      val provider = function("$type\$FeatureFlagContribution", "provides$type")
      assertTrue(provider.annotations.has("dev/zacsweers/metro/Provides"))
      assertTrue(provider.annotations.has("dev/zacsweers/metro/IntoSet"))
      assertEquals(
        KmClassifier.Class("com/squareup/featureflags/FeatureFlag"),
        provider.returnType.classifier,
      )
    }
  }

  private fun metadata(className: String): KmClass {
    val annotation = assertNotNull(Class.forName(className).getAnnotation(Metadata::class.java))
    return assertIs<KotlinClassMetadata.Class>(KotlinClassMetadata.readStrict(annotation)).kmClass
  }

  private fun function(type: String, name: String): KmFunction =
    metadata("com.squareup.test.lib.$type").functions.single { it.name == name }

  private fun List<KmAnnotation>.has(className: String): Boolean = any { it.className == className }
}
