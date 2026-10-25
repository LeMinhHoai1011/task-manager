package vn.smarttask.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * PH-7 basic ArchUnit (code scope): controllers contain no heavy persistence logic
 * (no direct jakarta.persistence / minio usage — all JPA goes through services and
 * Spring Data repositories), and pure domain helpers stay infrastructure-free.
 */
class ArchRulesTest {
  private final JavaClasses classes =
      new ClassFileImporter().importPackages("vn.smarttask");

  @Test
  void controllersHaveNoDirectPersistenceOrStorageLogic() {
    noClasses()
        .that().haveSimpleNameEndingWith("Controller")
        .should().dependOnClassesThat().resideInAnyPackage(
            "jakarta.persistence..", "io.minio..", "org.hibernate..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void domainHelpersHaveNoInfrastructureImports() {
    String[] pkgs = {"..files..", "..task..", "..security.."};
    for (String pkg : pkgs) {
      noClasses()
          .that().resideInAPackage(pkg)
          .and().haveSimpleNameEndingWith("Chain")
          .should().dependOnClassesThat().resideInAnyPackage(
              "org.springframework..", "jakarta.persistence..", "io.minio..")
          .allowEmptyShould(true)
          .check(classes);
      noClasses()
          .that().resideInAPackage(pkg)
          .and().haveSimpleNameEndingWith("Policy")
          .and().doNotHaveSimpleName("AuthorizationPolicy")
          .should().dependOnClassesThat().resideInAnyPackage(
              "org.springframework..", "jakarta.persistence..", "io.minio..")
          .allowEmptyShould(true)
          .check(classes);
      noClasses()
          .that().resideInAPackage(pkg)
          .and().haveSimpleNameEndingWith("Decider")
          .should().dependOnClassesThat().resideInAnyPackage(
              "org.springframework..", "jakarta.persistence..", "io.minio..")
          .allowEmptyShould(true)
          .check(classes);
      noClasses()
          .that().resideInAPackage(pkg)
          .and().haveSimpleNameEndingWith("Guard")
          .and().doNotHaveSimpleName("WorkflowGuard")
          .should().dependOnClassesThat().resideInAnyPackage(
              "org.springframework..", "jakarta.persistence..", "io.minio..")
          .allowEmptyShould(true)
          .check(classes);
    }
  }

  @Test
  void reportAndNotifyDomainHaveNoInfrastructureImports() {
    noClasses()
        .that().haveSimpleNameEndingWith("Dictionary")
        .or().haveSimpleNameEndingWith("Composer")
        .or().haveSimpleNameEndingWith("ExportJobManager")
        .or().haveSimpleNameEndingWith("NotificationCenter")
        .or().haveSimpleNameEndingWith("ReminderEngine")
        .or().haveSimpleNameEndingWith("DeadlineResolver")
        .or().haveSimpleNameEndingWith("CalendarView")
        .or().haveSimpleNameEndingWith("RateLimiter")
        .or().haveSimpleNameEndingWith("MetricsRegistry")
        .or().haveSimpleNameEndingWith("RedactingLogger")
        .or().haveSimpleNameEndingWith("StreamTicketManager")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework..", "jakarta.persistence..", "io.minio..")
        .allowEmptyShould(true)
        .check(classes);
  }
}
