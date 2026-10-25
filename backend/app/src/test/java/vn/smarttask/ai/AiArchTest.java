package vn.smarttask.ai;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * AI-03 structural guarantee: AiService must never touch WorkflowService mutation
 * paths, approval entities, or task persistence directly. The only task write path
 * is TaskService.create via accept() with actor=user.
 */
class AiArchTest {
  private final JavaClasses classes =
      new ClassFileImporter().importPackages("vn.smarttask");

  @Test
  void aiServiceDoesNotDependOnWorkflowMutation() {
    noClasses()
        .that().haveSimpleName("AiService")
        .should().dependOnClassesThat().haveSimpleNameEndingWith("WorkflowService")
        .allowEmptyShould(true)
        .check(classes);
    noClasses()
        .that().haveSimpleName("AiService")
        .should().dependOnClassesThat().resideInAPackage("..workflow..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void aiServiceDoesNotTouchTaskPersistence() {
    noClasses()
        .that().resideInAPackage("..ai..")
        .and().resideOutsideOfPackage("..ai.entity..")
        .and().resideOutsideOfPackage("..ai.repo..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "jakarta.persistence..", "io.minio..", "org.hibernate..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void aiPureHelpersStayInfrastructureFree() {
    noClasses()
        .that().haveSimpleNameEndingWith("Validator")
        .and().resideInAPackage("..ai..")
        .or().haveSimpleNameEndingWith("Sanitizer")
        .or().haveSimpleName("AiJobLifecycle")
        .or().haveSimpleName("MockOllamaClient")
        .or().haveSimpleName("HttpOllamaClient")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework..", "jakarta.persistence..", "io.minio..")
        .allowEmptyShould(true)
        .check(classes);
  }
}
