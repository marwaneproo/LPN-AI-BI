import type { Message } from "../types/qa.types";

export const defaultMessages: Message[] = [
  {
    id: "welcome",
    role: "assistant",
    text: "Pose une question business. Les nouvelles questions sont envoyées au backend Java, au Schema RAG, au modèle SQL local, puis au SQL Executor sécurisé.",
    status: "done",
    sources: [
      {
        table: "C_ORDER",
        module: "Sales",
        score: 0.91,
        description: "En-têtes de commandes clients avec dates, statuts, montants et partenaires.",
      },
    ],
    sql: "SELECT COUNT(*) AS total_orders\nFROM C_ORDER\nWHERE DATEORDERED >= date_trunc('month', CURRENT_DATE) - INTERVAL '1 month'\n  AND DATEORDERED < date_trunc('month', CURRENT_DATE);",
    rows: [{ total_orders: 18430 }],
    latency: 1310,
  },
];

export const exampleQuestions = [
  "How many orders were placed last month?",
  "Quel est le chiffre d'affaires par catégorie ?",
  "Quels produits risquent une rupture bientôt ?",
  "Liste les clients avec factures impayées.",
];
