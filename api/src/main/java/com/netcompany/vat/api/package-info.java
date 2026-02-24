/**
 * API module — Spring Boot application, REST controllers, and request/response DTOs.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Spring Boot application entry point ({@code VatApplication})</li>
 *   <li>REST controllers for tax returns, invoices, and filing obligations</li>
 *   <li>Bean Validation on inbound DTOs ({@code @Valid}, {@code @NotNull})</li>
 *   <li>Global exception handling ({@code @ControllerAdvice})</li>
 *   <li>Jurisdiction plugin registration via Spring {@code @Component} collection</li>
 * </ul>
 *
 * <p>This is the only deployable module. All other modules are library dependencies.
 * Virtual threads are enabled via {@code spring.threads.virtual.enabled=true}.
 */
package com.netcompany.vat.api;
