package com.rabbitlab.storefront.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * P2-M7 — Hexagonal mimarinin kuralları, derleyicinin yakalayamadığı kadarı. Biri yanlışlıkla
 * domain'e bir Spring annotation'ı eklerse bu test kırmızı olur; kural kod incelemesine kalmaz.
 *
 * <pre>
 *        adapter.in.*  ──▶  application.port.in  ◀── application (servisler) ──▶ application.port.out  ◀──  adapter.out.*
 *                                       └──────────────▶  domain  ◀──────────────┘
 * </pre>
 * Oklar bağımlılık yönü: hepsi içeriye, domain'e doğru. Domain hiçbir şeye bağımlı değil.
 * (Backoffice ile Storefront arasındaki sınırı ayrıca test etmeye gerek yok: ayrı Maven modülleri,
 * birbirlerini derleyici seviyesinde göremezler.)
 */
@AnalyzeClasses(packages = "com.rabbitlab.storefront", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domain_depends_on_nothing = noClasses()
            .that().resideInAPackage("..storefront.domain..")
            .should().dependOnClassesThat().resideOutsideOfPackages("..storefront.domain..", "java..")
            .because("domain iş kurallarıdır; Spring, JDBC, RabbitMQ, JSON veya use case'leri bilmez");

    @ArchTest
    static final ArchRule application_depends_only_on_domain = noClasses()
            .that().resideInAPackage("..storefront.application..")
            .should().dependOnClassesThat()
            .resideOutsideOfPackages("..storefront.application..", "..storefront.domain..", "java..")
            .because("use case'ler framework'süzdür; dış dünyaya sadece port arayüzleriyle dokunur");

    @ArchTest
    static final ArchRule message_contract_stays_in_adapters = noClasses()
            .that().resideInAnyPackage("..storefront.domain..", "..storefront.application..")
            .should().dependOnClassesThat().resideInAPackage("com.rabbitlab.contract..")
            .because("mesaj sözleşmesi kablonun formatıdır; içeriye çevrilerek girer (Faz 1 acıtan nokta 4)");

    @ArchTest
    static final ArchRule adapters_do_not_know_each_other = slices()
            .matching("..storefront.adapter.(*).(*)..")
            .should().notDependOnEachOther()
            .because("her adapter kendi teknolojisini sarar; birbirleriyle application üzerinden konuşurlar");

    @ArchTest
    static final ArchRule adapters_use_ports_not_services = noClasses()
            .that().resideInAPackage("..storefront.adapter..")
            .should().dependOnClassesThat(resideInAPackage("..storefront.application..").and(simpleNameEndingWith("Service")))
            .because("adapter use case'i somut servis üzerinden değil inbound port üzerinden çağırır");

    @ArchTest
    static final ArchRule nothing_depends_on_wiring = noClasses()
            .that().resideOutsideOfPackage("..storefront.config..")
            .should().dependOnClassesThat().resideInAPackage("..storefront.config..")
            .because("config her şeyi bilen tek yer (composition root); kimse ona bağımlı olmamalı");
}
