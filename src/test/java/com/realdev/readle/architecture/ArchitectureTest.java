package com.realdev.readle.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.realdev.readle",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule CONTROLLERS_SHOULD_NOT_DEPEND_ON_REPOSITORIES =
      noClasses()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..repository..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule SERVICES_SHOULD_NOT_DEPEND_ON_CONTROLLERS =
      noClasses()
          .that()
          .resideInAPackage("..service..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..controller..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule REPOSITORIES_SHOULD_NOT_DEPEND_ON_SERVICES =
      noClasses()
          .that()
          .resideInAPackage("..repository..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..service..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule ENTITIES_SHOULD_NOT_DEPEND_ON_OUTER_LAYERS =
      noClasses()
          .that()
          .resideInAPackage("..entity..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..controller..", "..dto..", "..config..", "..security..", "..web..")
          .allowEmptyShould(true);
}
