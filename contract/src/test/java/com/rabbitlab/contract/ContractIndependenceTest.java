package com.rabbitlab.contract;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * P2-M7 — Sözleşme iki context'in de bağımlılığı; bu yüzden kendisi hiçbir şeye bağımlı olmamalı.
 * Bir gün buraya Jackson annotation'ı eklenirse iki uygulama da Jackson sürümüne kilitlenir.
 */
@AnalyzeClasses(packages = "com.rabbitlab.contract", importOptions = ImportOption.DoNotIncludeTests.class)
class ContractIndependenceTest {

    @ArchTest
    static final ArchRule contract_depends_only_on_jdk = classes()
            .should().onlyDependOnClassesThat().resideInAnyPackage("com.rabbitlab.contract..", "java..")
            .because("sözleşme saf veri: framework, kütüphane ve context bilgisi taşımaz");
}
