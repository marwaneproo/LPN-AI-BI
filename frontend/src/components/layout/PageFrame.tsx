import type { ReactNode } from "react";

export function PageFrame({
  title,
  subtitle,
  children,
  headerAside,
  className = "",
  hideHeader = false,
}: {
  title: string;
  subtitle?: string;
  children: ReactNode;
  headerAside?: ReactNode;
  className?: string;
  hideHeader?: boolean;
}) {
  return (
    <section className={`page-frame ${className}`.trim()}>
      {!hideHeader ? (
        <div className="page-heading">
          <div className="page-title">
            <h1>{title}</h1>
            {subtitle ? <p>{subtitle}</p> : null}
          </div>
          {headerAside ? <div className="page-heading-aside">{headerAside}</div> : null}
        </div>
      ) : null}
      {children}
    </section>
  );
}
