\set ON_ERROR_STOP on

\if :{?db_name}
\else
\getenv db_name POSTGRES_DB
\endif

\if :{?app_admin_pwd}
\else
\getenv app_admin_pwd POSTGRES_APP_ADMIN_PASSWORD
\endif

\if :{?ai_pwd}
\else
\getenv ai_pwd POSTGRES_AI_READONLY_PASSWORD
\endif

DO
$$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lpn_app_admin') THEN
    CREATE ROLE lpn_app_admin LOGIN;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lpn_ai_readonly') THEN
    CREATE ROLE lpn_ai_readonly LOGIN;
  END IF;
END
$$;

ALTER ROLE lpn_app_admin WITH PASSWORD :'app_admin_pwd';
ALTER ROLE lpn_ai_readonly WITH PASSWORD :'ai_pwd';

GRANT CREATE ON DATABASE :"db_name" TO lpn_app_admin;

CREATE SCHEMA IF NOT EXISTS business AUTHORIZATION lpn_app_admin;
CREATE SCHEMA IF NOT EXISTS app AUTHORIZATION lpn_app_admin;

ALTER SCHEMA business OWNER TO lpn_app_admin;
ALTER SCHEMA app OWNER TO lpn_app_admin;

REVOKE ALL ON SCHEMA app FROM PUBLIC;
REVOKE ALL ON SCHEMA app FROM lpn_ai_readonly;

GRANT USAGE ON SCHEMA business TO lpn_ai_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA business TO lpn_ai_readonly;

ALTER DEFAULT PRIVILEGES FOR ROLE lpn_app_admin IN SCHEMA business
  GRANT SELECT ON TABLES TO lpn_ai_readonly;

CREATE TABLE IF NOT EXISTS app.ai_bi_users (
  id uuid PRIMARY KEY,
  username text NOT NULL,
  password_hash text NOT NULL,
  password_salt text NOT NULL DEFAULT '',
  role text NOT NULL CHECK (role IN ('ADMIN', 'USER')),
  status text NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  decided_at timestamptz,
  decided_by text
);

ALTER TABLE app.ai_bi_users OWNER TO lpn_app_admin;

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_bi_users_normalized_username
  ON app.ai_bi_users (lower(username));

CREATE INDEX IF NOT EXISTS idx_ai_bi_users_status
  ON app.ai_bi_users (status, created_at DESC);

CREATE TABLE IF NOT EXISTS app.ai_bi_sessions (
  token uuid PRIMARY KEY,
  token_hash char(64) UNIQUE,
  user_id uuid NOT NULL REFERENCES app.ai_bi_users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL
);

ALTER TABLE app.ai_bi_sessions OWNER TO lpn_app_admin;

CREATE INDEX IF NOT EXISTS idx_ai_bi_sessions_user_id
  ON app.ai_bi_sessions (user_id);

CREATE INDEX IF NOT EXISTS idx_ai_bi_sessions_expires_at
  ON app.ai_bi_sessions (expires_at);

CREATE TABLE IF NOT EXISTS app.ai_request_performance_trace (
  id uuid PRIMARY KEY,
  client_request_id text,
  endpoint text NOT NULL,
  question text,
  status text NOT NULL,
  error_message text,
  sql_model text,
  sql_fallback_model text,
  narrator_model text,
  model_used text,
  fallback_used boolean NOT NULL DEFAULT false,
  generated_sql text,
  retrieved_tables jsonb NOT NULL DEFAULT '[]'::jsonb,
  retrieved_table_count integer NOT NULL DEFAULT 0,
  frontend_started_at timestamptz,
  backend_received_at timestamptz NOT NULL,
  backend_completed_at timestamptz,
  frontend_completed_at timestamptz,
  client_total_latency_ms bigint,
  frontend_network_latency_ms bigint,
  backend_total_latency_ms bigint,
  schema_retrieval_latency_ms bigint,
  sql_model_latency_ms bigint,
  sql_normalization_latency_ms bigint,
  sql_execution_latency_ms bigint,
  narration_latency_ms bigint,
  ollama_load_duration_ms bigint,
  ollama_prompt_eval_duration_ms bigint,
  ollama_eval_duration_ms bigint,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE app.ai_request_performance_trace OWNER TO lpn_app_admin;

CREATE INDEX IF NOT EXISTS idx_ai_perf_trace_created_at
  ON app.ai_request_performance_trace (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_perf_trace_client_request_id
  ON app.ai_request_performance_trace (client_request_id);
