import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { promises as fs } from "node:fs";
import path from "node:path";
import { z } from "zod";

const server = new McpServer({
  name: "tax-core-mcp",
  version: "0.1.0"
});

const filingTypeSchema = z.enum(["regular", "zero", "correction"]);

function parseIsoDate(value: string): Date | null {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function isZero(value: number): boolean {
  return Math.abs(value) < 0.000001;
}

const workspaceRoot = path.resolve(process.cwd(), "..");
const analysisRoot = path.join(workspaceRoot, "analysis");
const architectureRoot = path.join(workspaceRoot, "architecture");
const designRoot = path.join(workspaceRoot, "design");
const criticalReviewRoot = path.join(workspaceRoot, "critical-review");
const optimizationRoot = path.join(workspaceRoot, "optimization");

const roleSchema = z.enum([
  "architect",
  "business_analyst",
  "designer",
  "critical_reviewer",
  "coding_optimizer"
]);

async function listMarkdownFiles(rootDir: string): Promise<string[]> {
  const entries = await fs.readdir(rootDir, { withFileTypes: true });
  const files: string[] = [];

  for (const entry of entries) {
    const fullPath = path.join(rootDir, entry.name);
    if (entry.isDirectory()) {
      const childFiles = await listMarkdownFiles(fullPath);
      files.push(...childFiles);
      continue;
    }

    if (entry.isFile() && entry.name.toLowerCase().endsWith(".md")) {
      files.push(fullPath);
    }
  }

  return files;
}

function toPosixRelative(from: string, to: string): string {
  return path.relative(from, to).split(path.sep).join("/");
}

async function pathExists(targetPath: string): Promise<boolean> {
  try {
    await fs.access(targetPath);
    return true;
  } catch {
    return false;
  }
}

async function listMarkdownFilesIfExists(rootDir: string): Promise<string[]> {
  if (!(await pathExists(rootDir))) {
    return [];
  }

  return listMarkdownFiles(rootDir);
}

function dedupeAndSort(values: string[]): string[] {
  return Array.from(new Set(values)).sort((a, b) => a.localeCompare(b));
}

async function getRoleContextFiles(role: z.infer<typeof roleSchema>): Promise<string[]> {
  const commonGovernance = [
    "ROLE_CONTEXT_POLICY.md",
    "CLAUDE.md",
    "README.md"
  ];

  const contractFiles = [
    "architect.md",
    "business-analyst.md",
    "DESIGNER.md",
    "CRITICAL_REVIEWER.md",
    "CODING_OPTIMIZER.md"
  ];

  const roleSpecificRoots: Record<z.infer<typeof roleSchema>, string[]> = {
    architect: [architectureRoot],
    business_analyst: [analysisRoot],
    designer: [architectureRoot, designRoot],
    critical_reviewer: [analysisRoot, architectureRoot, designRoot, criticalReviewRoot],
    coding_optimizer: [optimizationRoot, criticalReviewRoot]
  };

  const roots = roleSpecificRoots[role];
  const roleFiles = (await Promise.all(roots.map((root) => listMarkdownFilesIfExists(root)))).flat();

  const roleGovernance: Record<z.infer<typeof roleSchema>, string[]> = {
    architect: ["architect.md", "ROLE_CONTEXT_POLICY.md"],
    business_analyst: ["business-analyst.md", "ROLE_CONTEXT_POLICY.md"],
    designer: ["DESIGNER.md", "ROLE_CONTEXT_POLICY.md"],
    critical_reviewer: [...contractFiles, "ROLE_CONTEXT_POLICY.md", "README.md", "mcp-server/README.md"],
    coding_optimizer: [...contractFiles, ...commonGovernance]
  };

  const governanceFiles = await Promise.all(
    roleGovernance[role].map(async (relativePath) => {
      const absolutePath = path.join(workspaceRoot, relativePath);
      return (await pathExists(absolutePath)) ? absolutePath : null;
    })
  );

  return dedupeAndSort([...roleFiles, ...governanceFiles.filter((value): value is string => value !== null)]);
}

server.tool("health_check", "Returns a simple status payload.", async () => {
  return {
    content: [
      {
        type: "text",
        text: JSON.stringify({ status: "ok", service: "tax-core-mcp" }, null, 2)
      }
    ]
  };
});

server.tool(
  "get_business_analyst_context_index",
  "Lists all Markdown source documents in analysis/ used as business analyst context.",
  async () => {
    const files = (await listMarkdownFiles(analysisRoot)).sort((a, b) => a.localeCompare(b));
    const documentIndex = await Promise.all(
      files.map(async (filePath) => {
        const stat = await fs.stat(filePath);
        return {
          path: toPosixRelative(workspaceRoot, filePath),
          updatedAt: stat.mtime.toISOString(),
          sizeBytes: stat.size
        };
      })
    );

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify(
            {
              generatedAt: new Date().toISOString(),
              workspaceRoot: workspaceRoot.split(path.sep).join("/"),
              analysisRoot: toPosixRelative(workspaceRoot, analysisRoot),
              documentCount: documentIndex.length,
              documents: documentIndex
            },
            null,
            2
          )
        }
      ]
    };
  }
);

server.tool(
  "get_architect_context_index",
  "Lists all Markdown source documents in architecture/ used as architect context.",
  async () => {
    const files = (await listMarkdownFiles(architectureRoot)).sort((a, b) => a.localeCompare(b));
    const documentIndex = await Promise.all(
      files.map(async (filePath) => {
        const stat = await fs.stat(filePath);
        return {
          path: toPosixRelative(workspaceRoot, filePath),
          updatedAt: stat.mtime.toISOString(),
          sizeBytes: stat.size
        };
      })
    );

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify(
            {
              generatedAt: new Date().toISOString(),
              workspaceRoot: workspaceRoot.split(path.sep).join("/"),
              architectureRoot: toPosixRelative(workspaceRoot, architectureRoot),
              documentCount: documentIndex.length,
              documents: documentIndex
            },
            null,
            2
          )
        }
      ]
    };
  }
);

server.tool(
  "get_business_analyst_context_bundle",
  "Loads latest business analyst documents from analysis/. Reads files at runtime so updates are automatically reflected.",
  {
    includeContent: z.boolean().default(true),
    maxCharsPerFile: z.number().int().positive().max(200000).default(20000),
    paths: z.array(z.string().min(1)).optional()
  },
  async ({ includeContent, maxCharsPerFile, paths }) => {
    const allFiles = (await listMarkdownFiles(analysisRoot)).sort((a, b) => a.localeCompare(b));
    const fileSet = new Set(allFiles.map((f) => toPosixRelative(workspaceRoot, f)));
    const selectedRelativePaths =
      paths && paths.length > 0
        ? paths.map((p) => p.replace(/\\/g, "/")).filter((p) => fileSet.has(p))
        : Array.from(fileSet).sort((a, b) => a.localeCompare(b));

    const documents = await Promise.all(
      selectedRelativePaths.map(async (relativePath) => {
        const absolutePath = path.join(workspaceRoot, relativePath);
        const stat = await fs.stat(absolutePath);
        const text = includeContent ? await fs.readFile(absolutePath, "utf8") : "";
        const truncated = includeContent && text.length > maxCharsPerFile;

        return {
          path: relativePath,
          updatedAt: stat.mtime.toISOString(),
          sizeBytes: stat.size,
          truncated,
          content: includeContent ? (truncated ? `${text.slice(0, maxCharsPerFile)}\n...[TRUNCATED]` : text) : undefined
        };
      })
    );

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify(
            {
              generatedAt: new Date().toISOString(),
              source: "analysis-markdown-runtime-bundle",
              requestedPaths: paths ?? null,
              resolvedDocumentCount: documents.length,
              includeContent,
              maxCharsPerFile,
              documents
            },
            null,
            2
          )
        }
      ]
    };
  }
);

server.tool(
  "get_architect_context_bundle",
  "Loads latest architect documents from architecture/. Reads files at runtime so updates are automatically reflected.",
  {
    includeContent: z.boolean().default(true),
    maxCharsPerFile: z.number().int().positive().max(200000).default(20000),
    paths: z.array(z.string().min(1)).optional()
  },
  async ({ includeContent, maxCharsPerFile, paths }) => {
    const allFiles = (await listMarkdownFiles(architectureRoot)).sort((a, b) => a.localeCompare(b));
    const fileSet = new Set(allFiles.map((f) => toPosixRelative(workspaceRoot, f)));
    const selectedRelativePaths =
      paths && paths.length > 0
        ? paths.map((p) => p.replace(/\\/g, "/")).filter((p) => fileSet.has(p))
        : Array.from(fileSet).sort((a, b) => a.localeCompare(b));

    const documents = await Promise.all(
      selectedRelativePaths.map(async (relativePath) => {
        const absolutePath = path.join(workspaceRoot, relativePath);
        const stat = await fs.stat(absolutePath);
        const text = includeContent ? await fs.readFile(absolutePath, "utf8") : "";
        const truncated = includeContent && text.length > maxCharsPerFile;

        return {
          path: relativePath,
          updatedAt: stat.mtime.toISOString(),
          sizeBytes: stat.size,
          truncated,
          content: includeContent ? (truncated ? `${text.slice(0, maxCharsPerFile)}\n...[TRUNCATED]` : text) : undefined
        };
      })
    );

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify(
            {
              generatedAt: new Date().toISOString(),
              source: "architecture-markdown-runtime-bundle",
              requestedPaths: paths ?? null,
              resolvedDocumentCount: documents.length,
              includeContent,
              maxCharsPerFile,
              documents
            },
            null,
            2
          )
        }
      ]
    };
  }
);

server.tool(
  "get_role_context_index",
  "Lists all Markdown source documents in role-scoped context for the selected role.",
  {
    role: roleSchema
  },
  async ({ role }) => {
    const files = await getRoleContextFiles(role);
    const documentIndex = await Promise.all(
      files.map(async (filePath) => {
        const stat = await fs.stat(filePath);
        return {
          path: toPosixRelative(workspaceRoot, filePath),
          updatedAt: stat.mtime.toISOString(),
          sizeBytes: stat.size
        };
      })
    );

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify(
            {
              generatedAt: new Date().toISOString(),
              role,
              workspaceRoot: workspaceRoot.split(path.sep).join("/"),
              documentCount: documentIndex.length,
              documents: documentIndex
            },
            null,
            2
          )
        }
      ]
    };
  }
);

server.tool(
  "get_role_context_bundle",
  "Loads latest role-scoped documents for the selected role. Reads files at runtime so updates are automatically reflected.",
  {
    role: roleSchema,
    includeContent: z.boolean().default(true),
    maxCharsPerFile: z.number().int().positive().max(200000).default(20000),
    paths: z.array(z.string().min(1)).optional()
  },
  async ({ role, includeContent, maxCharsPerFile, paths }) => {
    const allFiles = await getRoleContextFiles(role);
    const fileSet = new Set(allFiles.map((f) => toPosixRelative(workspaceRoot, f)));
    const selectedRelativePaths =
      paths && paths.length > 0
        ? paths.map((p) => p.replace(/\\/g, "/")).filter((p) => fileSet.has(p))
        : Array.from(fileSet).sort((a, b) => a.localeCompare(b));

    const documents = await Promise.all(
      selectedRelativePaths.map(async (relativePath) => {
        const absolutePath = path.join(workspaceRoot, relativePath);
        const stat = await fs.stat(absolutePath);
        const text = includeContent ? await fs.readFile(absolutePath, "utf8") : "";
        const truncated = includeContent && text.length > maxCharsPerFile;

        return {
          path: relativePath,
          updatedAt: stat.mtime.toISOString(),
          sizeBytes: stat.size,
          truncated,
          content: includeContent ? (truncated ? `${text.slice(0, maxCharsPerFile)}\n...[TRUNCATED]` : text) : undefined
        };
      })
    );

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify(
            {
              generatedAt: new Date().toISOString(),
              role,
              source: "role-scoped-markdown-runtime-bundle",
              requestedPaths: paths ?? null,
              resolvedDocumentCount: documents.length,
              includeContent,
              maxCharsPerFile,
              documents
            },
            null,
            2
          )
        }
      ]
    };
  }
);

server.tool(
  "add_numbers",
  "Adds two numbers and returns the sum.",
  {
    a: z.number(),
    b: z.number()
  },
  async ({ a, b }) => {
    return {
      content: [
        {
          type: "text",
          text: JSON.stringify({ a, b, sum: a + b }, null, 2)
        }
      ]
    };
  }
);

server.tool(
  "create_vat_claim_stub",
  "Creates a draft claim payload from VAT totals.",
  {
    taxpayerId: z.string().min(1),
    periodStart: z.string().min(1),
    periodEnd: z.string().min(1),
    outputVatAmount: z.number(),
    inputVatDeductibleAmount: z.number(),
    adjustments: z.number().default(0)
  },
  async ({
    taxpayerId,
    periodStart,
    periodEnd,
    outputVatAmount,
    inputVatDeductibleAmount,
    adjustments
  }) => {
    const netVatAmount = outputVatAmount - inputVatDeductibleAmount + adjustments;
    const resultType = netVatAmount > 0 ? "payable" : netVatAmount < 0 ? "refund" : "zero";

    const payload = {
      claimId: `claim-${taxpayerId}-${periodEnd}`,
      taxpayerId,
      periodStart,
      periodEnd,
      resultType,
      amount: Math.abs(netVatAmount),
      currency: "DKK",
      calculationReference: {
        outputVatAmount,
        inputVatDeductibleAmount,
        adjustments,
        netVatAmount
      }
    };

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify(payload, null, 2)
        }
      ]
    };
  }
);

server.tool(
  "validate_dk_vat_filing",
  "Validates Danish VAT filing fields and returns derived claim inputs.",
  {
    cvrNumber: z.string().min(1),
    taxPeriodStart: z.string().min(1),
    taxPeriodEnd: z.string().min(1),
    filingType: filingTypeSchema,
    hadTaxableActivity: z.boolean().optional(),
    outputVatAmount: z.number().default(0),
    inputVatDeductibleAmount: z.number().default(0),
    vatOnGoodsPurchasesAbroadAmount: z.number().default(0),
    vatOnServicesPurchasesAbroadAmount: z.number().default(0),
    rubrikAGoodsEuPurchaseValue: z.number().default(0),
    rubrikAServicesEuPurchaseValue: z.number().default(0),
    rubrikBGoodsEuSaleValue: z.number().default(0),
    rubrikBServicesEuSaleValue: z.number().default(0),
    rubrikCOtherVatExemptSuppliesValue: z.number().default(0),
    adjustments: z.number().default(0)
  },
  async ({
    cvrNumber,
    taxPeriodStart,
    taxPeriodEnd,
    filingType,
    hadTaxableActivity,
    outputVatAmount,
    inputVatDeductibleAmount,
    vatOnGoodsPurchasesAbroadAmount,
    vatOnServicesPurchasesAbroadAmount,
    rubrikAGoodsEuPurchaseValue,
    rubrikAServicesEuPurchaseValue,
    rubrikBGoodsEuSaleValue,
    rubrikBServicesEuSaleValue,
    rubrikCOtherVatExemptSuppliesValue,
    adjustments
  }) => {
    const errors: string[] = [];
    const warnings: string[] = [];

    if (!/^\d{8}$/.test(cvrNumber)) {
      errors.push("CVR number must be exactly 8 digits.");
    }

    const start = parseIsoDate(taxPeriodStart);
    const end = parseIsoDate(taxPeriodEnd);

    if (!start) {
      errors.push("taxPeriodStart must be a valid date string.");
    }

    if (!end) {
      errors.push("taxPeriodEnd must be a valid date string.");
    }

    if (start && end && start > end) {
      errors.push("taxPeriodStart must be before or equal to taxPeriodEnd.");
    }

    const nonNegativeFields: Array<[string, number]> = [
      ["outputVatAmount", outputVatAmount],
      ["inputVatDeductibleAmount", inputVatDeductibleAmount],
      ["vatOnGoodsPurchasesAbroadAmount", vatOnGoodsPurchasesAbroadAmount],
      ["vatOnServicesPurchasesAbroadAmount", vatOnServicesPurchasesAbroadAmount],
      ["rubrikAGoodsEuPurchaseValue", rubrikAGoodsEuPurchaseValue],
      ["rubrikAServicesEuPurchaseValue", rubrikAServicesEuPurchaseValue],
      ["rubrikBGoodsEuSaleValue", rubrikBGoodsEuSaleValue],
      ["rubrikBServicesEuSaleValue", rubrikBServicesEuSaleValue],
      ["rubrikCOtherVatExemptSuppliesValue", rubrikCOtherVatExemptSuppliesValue]
    ];

    for (const [name, value] of nonNegativeFields) {
      if (!Number.isFinite(value)) {
        errors.push(`${name} must be a finite number.`);
      }

      if (value < 0) {
        errors.push(`${name} cannot be negative.`);
      }
    }

    if (!Number.isFinite(adjustments)) {
      errors.push("adjustments must be a finite number.");
    }

    const monetaryTotal =
      outputVatAmount +
      inputVatDeductibleAmount +
      vatOnGoodsPurchasesAbroadAmount +
      vatOnServicesPurchasesAbroadAmount +
      Math.abs(adjustments);

    const internationalValueTotal =
      rubrikAGoodsEuPurchaseValue +
      rubrikAServicesEuPurchaseValue +
      rubrikBGoodsEuSaleValue +
      rubrikBServicesEuSaleValue +
      rubrikCOtherVatExemptSuppliesValue;

    const anyDeclaredAmounts = !isZero(monetaryTotal) || !isZero(internationalValueTotal);

    if (filingType === "zero") {
      if (anyDeclaredAmounts) {
        errors.push("Zero filing cannot contain VAT or international declaration amounts.");
      }

      if (hadTaxableActivity === true) {
        errors.push("Zero filing conflicts with hadTaxableActivity=true.");
      }
    }

    if ((filingType === "regular" || filingType === "correction") && hadTaxableActivity === false && anyDeclaredAmounts) {
      warnings.push("Amounts were provided while hadTaxableActivity=false.");
    }

    if (hadTaxableActivity === true && !anyDeclaredAmounts) {
      warnings.push("hadTaxableActivity=true but all declared amounts are zero.");
    }

    if (
      (vatOnGoodsPurchasesAbroadAmount > 0 || vatOnServicesPurchasesAbroadAmount > 0) &&
      isZero(rubrikAGoodsEuPurchaseValue) &&
      isZero(rubrikAServicesEuPurchaseValue)
    ) {
      warnings.push("Abroad purchase VAT is declared but Rubrik A values are zero.");
    }

    if (
      (rubrikBGoodsEuSaleValue > 0 || rubrikBServicesEuSaleValue > 0) &&
      isZero(outputVatAmount)
    ) {
      warnings.push("Rubrik B values exist while outputVatAmount is zero. Confirm domestic/exempt treatment.");
    }

    const netVatAmount = outputVatAmount - inputVatDeductibleAmount + adjustments;
    const resultType = netVatAmount > 0 ? "payable" : netVatAmount < 0 ? "refund" : "zero";

    const periodDays =
      start && end ? Math.floor((end.getTime() - start.getTime()) / (24 * 60 * 60 * 1000)) + 1 : null;

    const result = {
      valid: errors.length === 0,
      errors,
      warnings,
      derived: {
        netVatAmount,
        resultType,
        claimAmount: Math.abs(netVatAmount),
        periodDays,
        totalInternationalValue: internationalValueTotal,
        currency: "DKK"
      }
    };

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify(result, null, 2)
        }
      ]
    };
  }
);

server.tool(
  "evaluate_dk_vat_filing_obligation",
  "Evaluates Danish VAT filing obligation, cadence, and return type from basic taxpayer facts.",
  {
    isVatRegistered: z.boolean(),
    annualTaxableTurnoverDkk: z.number().min(0),
    isNewBusiness: z.boolean().default(false),
    optedMonthly: z.boolean().default(false),
    optedQuarterly: z.boolean().default(false),
    hasActivityThisPeriod: z.boolean(),
    hasSubmittedReturn: z.boolean().default(false),
    returnDueDatePassed: z.boolean().default(false),
    reportsEuSalesWithoutVat: z.boolean().default(false)
  },
  async ({
    isVatRegistered,
    annualTaxableTurnoverDkk,
    isNewBusiness,
    optedMonthly,
    optedQuarterly,
    hasActivityThisPeriod,
    hasSubmittedReturn,
    returnDueDatePassed,
    reportsEuSalesWithoutVat
  }) => {
    const mustRegister = annualTaxableTurnoverDkk >= 50000;
    const cadenceReason: string[] = [];

    if (!isVatRegistered) {
      const result = {
        filingObligation: "none",
        registration: {
          mustRegister,
          recommendation: mustRegister
            ? "Register for VAT now (turnover threshold reached)."
            : "No mandatory VAT registration from turnover threshold alone."
        },
        returnType: "none",
        filingCadence: "none",
        complianceStatus: "not_applicable",
        risks: mustRegister ? ["Operating above threshold without VAT registration risk."] : []
      };

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(result, null, 2)
          }
        ]
      };
    }

    let filingCadence: "monthly" | "quarterly" | "half_yearly";

    if (optedMonthly || annualTaxableTurnoverDkk > 50000000) {
      filingCadence = "monthly";
      cadenceReason.push(
        optedMonthly ? "Taxpayer opted in for monthly reporting." : "Turnover above DKK 50M."
      );
    } else if (optedQuarterly || isNewBusiness || annualTaxableTurnoverDkk >= 5000000) {
      filingCadence = "quarterly";
      if (optedQuarterly) {
        cadenceReason.push("Taxpayer opted in for quarterly reporting.");
      }

      if (isNewBusiness) {
        cadenceReason.push("New businesses are typically placed on quarterly cadence.");
      }

      if (annualTaxableTurnoverDkk >= 5000000) {
        cadenceReason.push("Turnover at or above DKK 5M.");
      }
    } else {
      filingCadence = "half_yearly";
      cadenceReason.push("Turnover below DKK 5M and no cadence override.");
    }

    const returnType = hasActivityThisPeriod ? "regular" : "zero";

    const risks: string[] = [];
    if (!hasSubmittedReturn && returnDueDatePassed) {
      risks.push("Return appears overdue; late filing can trigger fees or estimated assessment.");
    }

    if (reportsEuSalesWithoutVat) {
      risks.push("EU sales without VAT may require separate periodic reporting obligations.");
    }

    const result = {
      filingObligation: "required",
      registration: {
        mustRegister,
        recommendation: "Already VAT registered."
      },
      returnType,
      filingCadence,
      cadenceReason,
      complianceStatus: hasSubmittedReturn
        ? "submitted"
        : returnDueDatePassed
          ? "overdue"
          : "due",
      risks
    };

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify(result, null, 2)
        }
      ]
    };
  }
);

async function main(): Promise<void> {
  const transport = new StdioServerTransport();
  await server.connect(transport);
}

main().catch((error) => {
  console.error("MCP server failed to start:", error);
  process.exit(1);
});
